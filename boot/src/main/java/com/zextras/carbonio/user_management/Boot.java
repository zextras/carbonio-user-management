// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;


import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.zextras.carbonio.user_management.config.UserManagementModule;
import com.zextras.mailbox.client.service.ServiceClient;
import java.net.InetSocketAddress;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.slf4j.LoggerFactory;

public class Boot {

  private static Injector injector;
  private static final Logger logger = (Logger) LoggerFactory.getLogger("ROOT");

  public static void main(String[] args) throws Exception {
    logger.setLevel(Level.INFO);
    injector = Guice.createInjector(new UserManagementModule());
    // Force the initialization of the mailbox client pool (they require some seconds to start up)
    injector.getInstance(ServiceClient.class);
    injector.getInstance(Boot.class).boot();
  }

  public void boot() throws Exception {
    Server server = new Server(InetSocketAddress.createUnresolved("127.78.0.5", 10_000));
    ServletContextHandler servletHandler = new ServletContextHandler(server, "/");
    servletHandler.addEventListener(
      injector.getInstance(GuiceResteasyBootstrapServletContextListener.class)
    );

    ServletHolder servletHolder = new ServletHolder(HttpServletDispatcher.class);

    servletHandler.addServlet(servletHolder, "/*");
    server.setHandler(servletHandler);

    server.start();
    server.join();
  }

}
