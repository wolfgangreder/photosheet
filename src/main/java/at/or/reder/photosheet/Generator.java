/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package at.or.reder.photosheet;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JRField;
import net.sf.jasperreports.engine.JRRewindableDataSource;
import net.sf.jasperreports.engine.JRVirtualizer;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.fill.JRFileVirtualizer;
import net.sf.jasperreports.engine.xml.JRXmlLoader;
import net.sf.jasperreports.export.PdfExporterConfiguration;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;
import net.sf.jasperreports.export.type.PdfVersionEnum;

public final class Generator
{

  private static final class UncheckedJRExceptionWrapper extends Error
  {

    private final JRException wrapped;

    public UncheckedJRExceptionWrapper(JRException wrapped)
    {
      this.wrapped = wrapped;
    }

  }

  public static final void exportPrint2PDF(JasperPrint print,
                                           OutputStream out)
  {
    try {
      SimplePdfExporterConfiguration params = new SimplePdfExporterConfiguration();
      params.setCompressed(true);
      params.setPdfVersion(PdfVersionEnum.VERSION_1_7);
      params.setPermissions(PdfExporterConfiguration.ALL_PERMISSIONS);
      JRPdfExporter exporter = new JRPdfExporter();
      exporter.setConfiguration(params);

      exporter.setExporterInput(new SimpleExporterInput(print));
      exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));

      exporter.exportReport();
    } catch (JRException ex) {
      throw new UncheckedJRExceptionWrapper(ex);
    }
  }

  public static final void generatePDFReport(Collection<URL> files,
                                             Map<String, String> extraParams,
                                             InputStream jrxmlStream,
                                             OutputStream output) throws JRException, IOException
  {
    try {
      generateReport(files,
                     extraParams,
                     jrxmlStream,
                     (print) -> exportPrint2PDF(print,
                                                output));
    } catch (UncheckedJRExceptionWrapper w) {
      throw w.wrapped;
    }
  }

  public static InputStream getDefaultReport() throws IOException
  {
    URL u = Generator.class.getResource("IS_A4.jrxml");
    if (u != null) {
      return u.openStream();
    }
    return null;
  }

  private static final class DataSource implements JRRewindableDataSource
  {

    private final Collection<URL> files;
    private Iterator<URL> iterator;
    private URL current;
    private File tmpFile = null;
    private final Map<String, String> extraParams;

    public DataSource(Collection<URL> files,
                      Map<String, String> extraParams)
    {
      this.files = Collections.unmodifiableList(files.stream().
              sorted(Comparator.comparing(URL::toString)).
              collect(Collectors.toList()));
      iterator = this.files.iterator();
      if (extraParams != null && !extraParams.isEmpty()) {
        this.extraParams = Collections.unmodifiableMap(new HashMap<>(extraParams));
      } else {
        this.extraParams = Collections.emptyMap();
      }
    }

    @Override
    public void moveFirst() throws JRException
    {
      iterator = files.iterator();
    }

    private File createTmpFile(URL current) throws IOException
    {
      File tmp = File.createTempFile("psgenerator",
                                     ".jpeg");
      tmp.deleteOnExit();
      BufferedImage img = ImageIO.read(current);
      float w = img.getWidth();
      float h = img.getHeight();
      float scale = Math.max(w,
                             h) / 1400f;
      w /= scale;
      h /= scale;
      BufferedImage target = new BufferedImage((int) w,
                                               (int) h,
                                               BufferedImage.TYPE_INT_RGB);
      Graphics2D g = target.createGraphics();
      try {
        g.drawImage(img,
                    0,
                    0,
                    (int) w,
                    (int) h,
                    null);
      } finally {
        g.dispose();
      }
      try (OutputStream out = new FileOutputStream(tmp)) {
        ImageIO.write(target,
                      "JPEG",
                      out);
      }
      return tmp;
    }

    @Override
    public boolean next() throws JRException
    {
      if (iterator.hasNext()) {
        if (tmpFile != null) {
          tmpFile.delete();
        }
        current = iterator.next();
        try {
          tmpFile = createTmpFile(current);
        } catch (IOException ex) {
          throw new JRException(ex);
        }
      } else {
        current = null;
        tmpFile.delete();
      }
      return current != null;
    }

    @Override
    public Object getFieldValue(JRField jrField) throws JRException
    {
      switch (jrField.getName()) {
        case "IMAGE_PATH": {
          try {
            return tmpFile.toURI().toURL();
          } catch (MalformedURLException ex) {
            throw new JRException(ex);
          }
        }
        case "IMAGE_FILE": {
          String tmp = current.getFile();
          int pos = tmp.lastIndexOf(File.separatorChar);
          if (pos > 0) {
            tmp = tmp.substring(pos + 1);
          }
          pos = tmp.lastIndexOf('.');
          if (pos > 0) {
            tmp = tmp.substring(0,
                                pos);
          }
          return tmp;
        }
        case "COMMITTER": {
          return extraParams.get("COMMITTER");
        }
      }
      return null;
    }

  }

  private static String loadSWVersion()
  {
    URL u = Generator.class.getResource("/META-INF/maven/com.reder/Photosheet/pom.properties");
    if (u != null) {
      try (InputStream is = u.openStream()) {
        Properties props = new Properties();
        props.load(is);
        return "Version " + props.getProperty("version");
      } catch (IOException ex) {
        Logger.getLogger(Generator.class.getName()).log(Level.SEVERE,
                                                        null,
                                                        ex);
      }
    }
    return "Version ???";
  }

  public static void generateReport(Collection<URL> files,
                                    Map<String, String> extraParams,
                                    InputStream jrxmlStream,
                                    Consumer<JasperPrint> reportConsumer) throws JRException, IOException
  {
    JasperDesign design;
    try (InputStream is = jrxmlStream != null ? jrxmlStream : getDefaultReport()) {
      design = JRXmlLoader.load(is);
    }
    JasperReport report = JasperCompileManager.compileReport(design);
    Map<String, Object> parameters = new HashMap<>();
    if (extraParams != null) {
      parameters.putAll(extraParams);
    }
    parameters.put("SW_VERSION",
                   loadSWVersion());
    JRVirtualizer virt = new JRFileVirtualizer(10);
    parameters.put("REPORT_VIRTUALIZER",
                   virt);
    JRDataSource source = new DataSource(files,
                                         extraParams);
    JasperPrint print = JasperFillManager.fillReport(report,
                                                     parameters,
                                                     source);
    reportConsumer.accept(print);
  }

}
