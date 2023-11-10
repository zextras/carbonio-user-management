// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;


import com.google.inject.Guice;
import com.google.inject.Injector;
import com.zextras.carbonio.user_management.config.UserManagementModule;
import com.zextras.mailbox.client.service.ServiceClient;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

import java.net.InetSocketAddress;

public class Boot {

  private static Injector injector;

  public static void main(String[] args) throws Exception {
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
