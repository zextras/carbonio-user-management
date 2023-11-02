// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.zextras.carbonio.user_management.config.UserManagementModule;
import io.swagger.models.HttpMethod;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.util.Objects;

public final class Simulator implements AutoCloseable {

  private final static String MAILBOX_SERVICE_IP = "127.78.0.5";
  private final static int MAILBOX_SERVICE_PORT = 20_000;
  private final static String MAILBOX_SERVICE_URI =
    String.format("http://%s:%d", MAILBOX_SERVICE_IP, MAILBOX_SERVICE_PORT);

  private final Injector injector;
  private final SoapHttpUtils soapHttpUtils;

  private ClientAndServer clientAndServer;
  private MockServerClient mailboxServiceMock;
  private Server jettyServer;
  private LocalConnector httpLocalConnector;

  private Simulator() {
    this.injector = Guice.createInjector(new UserManagementModule());
    this.soapHttpUtils = new SoapHttpUtils();
  }

  private void startMailboxService() {
    clientAndServer = ClientAndServer.startClientAndServer(MAILBOX_SERVICE_PORT);
    mailboxServiceMock = new MockServerClient(MAILBOX_SERVICE_IP, MAILBOX_SERVICE_PORT);

    try {
      final byte[] wsdl = IOUtils.toByteArray(Objects.requireNonNull(
        getClass().getClassLoader().getResourceAsStream("soap/ZimbraService.wsdl")
      ));

      mailboxServiceMock
        .when(HttpRequest
          .request()
          .withMethod(HttpMethod.GET.toString())
          .withPath("/service/wsdl/ZimbraService.wsdl")
        )
        .respond(HttpResponse.response().withStatusCode(200).withBody(BinaryBody.binary(wsdl)));
    } catch (IOException exception) {
      throw new RuntimeException(exception);
    }
  }

  private void stopMailboxService() {
    if (mailboxServiceMock != null && mailboxServiceMock.hasStarted()) {
      mailboxServiceMock.stop();
      clientAndServer.stop();
    }
  }

  private void startJettyServer() {
    try {
      jettyServer = new Server();
      httpLocalConnector = new LocalConnector(jettyServer);
      jettyServer.addConnector(httpLocalConnector);
      ServletContextHandler servletHandler = new ServletContextHandler(jettyServer, "/");

      servletHandler.addEventListener(
        injector.getInstance(GuiceResteasyBootstrapServletContextListener.class)
      );

      ServletHolder servletHolder = new ServletHolder(HttpServletDispatcher.class);

      servletHandler.addServlet(servletHolder, "/*");
      jettyServer.setHandler(servletHandler);

      jettyServer.start();
    } catch (Exception exception) {
      throw new RuntimeException(exception);
    }
  }

  private void stopJettyServer() {
    if (jettyServer != null) {
      try {
        jettyServer.stop();
      } catch (Exception exception) {
        throw new RuntimeException(exception);
      }
    }
  }

  public Simulator start() {
    startJettyServer();
    return this;
  }

  public void resetAll() {
    mailboxServiceMock.reset();
  }

  public void stopAll() {
    stopJettyServer();
    stopMailboxService();
  }

  @Override
  public void close() {
    stopAll();
  }

  public MockServerClient getMailboxServiceMock() {
    return mailboxServiceMock;
  }

  public LocalConnector getHttpLocalConnector() {
    return httpLocalConnector;
  }

  public SoapHttpUtils getSoapHttpUtils() {return soapHttpUtils;}

  public final static class SimulatorBuilder {

    private Simulator simulator;

    public static SimulatorBuilder aSimulator() {
      return new SimulatorBuilder();
    }

    public SimulatorBuilder init() {
      simulator = new Simulator();
      return this;
    }

    public SimulatorBuilder withMailboxService() {
      simulator.startMailboxService();
      return this;
    }

    public Simulator build() {
      return simulator;
    }

  }
}
