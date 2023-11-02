// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;


import client.SoapClient;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.zextras.carbonio.user_management.config.UserManagementConfig;
import com.zextras.carbonio.user_management.config.UserManagementModule;
import java.net.InetSocketAddress;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

public class Boot {

  private static Injector injector;

  public static void main(String[] args) throws Exception {
    injector = Guice.createInjector(new UserManagementModule());

    UserManagementConfig config = injector.getInstance(UserManagementConfig.class);
    SoapClient.init(config.getProperties().getProperty("carbonio.mailbox.url"));
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
