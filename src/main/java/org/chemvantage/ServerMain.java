package org.chemvantage;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import org.apache.tomcat.util.scan.StandardJarScanner;
import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.apache.jsp.JettyJasperInitializer;
import org.eclipse.jetty.jsp.JettyJspServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.AbstractLifeCycle;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebInfConfiguration;

/**
 * Starts up the server, including a DefaultServlet that handles static files,
 * and any servlet classes annotated with the @WebServlet annotation.
 */
public class ServerMain {

  public static void main(String[] args) throws Exception {

    // Create a server that listens on port 8080.
    Server server = new Server(8080);
    WebAppContext webAppContext = new WebAppContext();
    server.setHandler(webAppContext);

    // Load static content from inside the jar file.
    URL webAppDir =
        ServerMain.class.getClassLoader().getResource("META-INF/resources");
    webAppContext.setResourceBase(webAppDir.toURI().toString());

    // Enable annotations so the server sees classes annotated with @WebServlet.
    webAppContext.setConfigurations(new Configuration[]{
        new AnnotationConfiguration(),
        new WebInfConfiguration(),
    });

    // Look for annotations in the classes directory (dev server) and in the
    // jar file (live server)
    webAppContext.setAttribute(
        "org.eclipse.jetty.server.webapp.ContainerIncludeJarPattern",
        ".*/target/classes/|.*\\.jar");

    // Handle static resources, e.g. html files.
    webAppContext.addServlet(DefaultServlet.class, "/");

    // Configure JSP support.
    enableEmbeddedJspSupport(webAppContext);

    // Start the server! 🚀
    server.start();
    System.out.println("Server started!");

    // Keep the main thread alive while the server is running.
    server.join();
  }

  /**
   * Setup JSP Support for ServletContextHandlers.
   * <p>
   *   NOTE: This is not required or appropriate if using a WebAppContext.
   * </p>
   *
   * @param servletContextHandler the ServletContextHandler to configure
   * @throws IOException if unable to configure
   */
  private static void enableEmbeddedJspSupport(ServletContextHandler servletContextHandler) throws IOException
  {
    // Establish Scratch directory for the servlet context (used by JSP compilation)
    File tempDir = new File(System.getProperty("java.io.tmpdir"));
    File scratchDir = new File(tempDir.toString(), "embedded-jetty-jsp");

    if (!scratchDir.exists())
    {
      if (!scratchDir.mkdirs())
      {
        throw new IOException("Unable to create scratch directory: " + scratchDir);
      }
    }
    servletContextHandler.setAttribute("javax.servlet.context.tempdir", scratchDir);

    // Set Classloader of Context to be sane (needed for JSTL)
    // JSP requires a non-System classloader, this simply wraps the
    // embedded System classloader in a way that makes it suitable
    // for JSP to use
    ClassLoader jspClassLoader = new URLClassLoader(new URL[0], ServerMain.class.getClassLoader());
    servletContextHandler.setClassLoader(jspClassLoader);

    // Manually call JettyJasperInitializer on context startup
    servletContextHandler.addBean(new JspStarter(servletContextHandler));

    // Create / Register JSP Servlet (must be named "jsp" per spec)
    ServletHolder holderJsp = new ServletHolder("jsp", JettyJspServlet.class);
    holderJsp.setInitOrder(0);
    holderJsp.setInitParameter("logVerbosityLevel", "DEBUG");
    holderJsp.setInitParameter("fork", "false");
    holderJsp.setInitParameter("xpoweredBy", "false");
    holderJsp.setInitParameter("compilerTargetVM", "1.8");
    holderJsp.setInitParameter("compilerSourceVM", "1.8");
    holderJsp.setInitParameter("keepgenerated", "true");
    servletContextHandler.addServlet(holderJsp, "*.jsp");
  }

  /**
   * JspStarter for embedded ServletContextHandlers
   *
   * This is added as a bean that is a jetty LifeCycle on the ServletContextHandler.
   * This bean's doStart method will be called as the ServletContextHandler starts,
   * and will call the ServletContainerInitializer for the jsp engine.
   *
   */
  public static class JspStarter extends AbstractLifeCycle implements ServletContextHandler.ServletContainerInitializerCaller
  {
    JettyJasperInitializer sci;
    ServletContextHandler context;

    public JspStarter (ServletContextHandler context)
    {
      this.sci = new JettyJasperInitializer();
      this.context = context;
      this.context.setAttribute("org.apache.tomcat.JarScanner", new StandardJarScanner());
    }

    @Override
    protected void doStart() throws Exception
    {
      ClassLoader old = Thread.currentThread().getContextClassLoader();
      Thread.currentThread().setContextClassLoader(context.getClassLoader());
      try
      {
        sci.onStartup(null, context.getServletContext());
        super.doStart();
      }
      finally
      {
        Thread.currentThread().setContextClassLoader(old);
      }
    }
  }
}