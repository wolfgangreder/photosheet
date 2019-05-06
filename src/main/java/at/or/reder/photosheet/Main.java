/*
 * Copyright 2019 Wolfgang Reder.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package at.or.reder.photosheet;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.JRException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 *
 * @author Wolfgang Reder
 */
public class Main
{

  private static final Options OPTIONS = new Options().
          addOption("i",
                    "input",
                    true,
                    "").
          addOption("o",
                    "output",
                    true,
                    "").
          addOption("f",
                    "format",
                    true,
                    "").
          addOption("c",
                    "commiter",
                    true,
                    "");
  private static final Set<String> SUFFIXES = Collections.unmodifiableSet(Arrays.asList(ImageIO.getReaderFileSuffixes()).
          stream().
          map(String::toLowerCase).
          collect(Collectors.toSet()));

  private static boolean isImageFile(Path path,
                                     BasicFileAttributes attr)
  {
    if (attr.isRegularFile() && Files.isReadable(path)) {
      String str = path.toString();
      int lastDotPos = str.lastIndexOf('.');
      if (lastDotPos > 0) {
        String ext = str.substring(lastDotPos + 1);
        return SUFFIXES.contains(ext.toLowerCase());
      }
    }
    return false;
  }

  private static LineNumberReader createReader(Path path) throws IOException
  {
    if (path != null) {
      return new LineNumberReader(Files.newBufferedReader(path));
    } else {
      return new LineNumberReader(new InputStreamReader(System.in));
    }
  }

  private static URL pathToURL(Path p)
  {
    if (p != null) {
      try {
        return p.toUri().toURL();
      } catch (MalformedURLException ex) {
      }
    }
    return null;
  }

  private static List<URL> createInput(CommandLine cmdLine) throws IOException
  {
    Path p = null;
    if (cmdLine.hasOption('i')) {
      String tmp = cmdLine.getOptionValue('i');
      p = Paths.get(tmp);
      if (Files.isDirectory(p)) {
        return Files.find(p,
                          1,
                          Main::isImageFile).
                map(Main::pathToURL).
                collect(Collectors.toList());
      }
    }

    List<URL> result = new ArrayList<>();
    try (LineNumberReader reader = createReader(p)) {
      String line;
      while ((line = reader.readLine()) != null) {
        p = Paths.get(line);
        if (Files.isReadable(p)) {
          URL u = pathToURL(p);
          BasicFileAttributes attr = Files.readAttributes(p,
                                                          BasicFileAttributes.class);
          if (isImageFile(p,
                          attr)) {
            result.add(u);
          }
        }
      }
    }
    return result;
  }

  private static OutputStream createOutputStream(CommandLine line) throws IOException
  {
    if (line.hasOption('o')) {
      Path p = Paths.get(line.getOptionValue('o'));
      return Files.newOutputStream(p,
                                   StandardOpenOption.TRUNCATE_EXISTING,
                                   StandardOpenOption.CREATE);
    }
    return System.out;
  }

  private static String getCommitter(CommandLine line) throws IOException
  {
    if (line.hasOption('c')) {
      String tmp = line.getOptionValue('c');
      if (tmp != null) {
        if (tmp.startsWith("@")) {
          try {
            Path p = Paths.get(tmp.substring(1));
            if (Files.isReadable(p)) {
              StringBuilder builder = new StringBuilder();
              try (Reader reader = Files.newBufferedReader(p)) {
                char[] buffer = new char[512];
                int read;
                while ((read = reader.read(buffer)) > 0) {
                  builder.append(buffer,
                                 0,
                                 read);
                }
              }
              return builder.toString();
            }
          } catch (Throwable th) {
          }
        } else {
          return tmp;
        }
      }
    }
    return null;
  }

  public static void main(String[] args)
  {
    CommandLineParser parse = new DefaultParser();
    try {
      CommandLine line = parse.parse(OPTIONS,
                                     args);
      List<URL> urls = createInput(line);
      Map<String, String> extraOpts = new HashMap<>();
      extraOpts.put("COMMITTER",
                    getCommitter(line));
      try (OutputStream out = createOutputStream(line)) {
        Generator.generatePDFReport(urls,
                                    extraOpts,
                                    null,
                                    out);
      }
    } catch (JRException | IOException ex) {
      Logger.getLogger(Main.class.getName()).log(Level.SEVERE,
                                                 null,
                                                 ex);
    } catch (ParseException ex) {
      System.out.println(OPTIONS.toString());
    }
  }

}
