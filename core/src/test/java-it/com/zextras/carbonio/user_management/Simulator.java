// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.zextras.carbonio.user_management.config.UserManagementModule;
import com.zextras.mailbox.client.MailboxClient;
import com.zextras.mailbox.client.service.ServiceClient;
import io.swagger.models.HttpMethod;
import org.eclipse.jetty.server.LocalConnector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.jboss.resteasy.plugins.guice.GuiceResteasyBootstrapServletContextListener;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class Simulator implements AutoCloseable {

  private final static String MAILBOX_SERVICE_IP = "127.78.0.5";
  private final static int MAILBOX_SERVICE_PORT = 20_000;

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
    initMailboxServiceClient();
  }

  private void initMailboxServiceClient() {
    setupWsdl();
    // NOTE: this line is used to initialize the ServiceClient with the mockServer properly
    // configured for the WSDL
    injector.getInstance(ServiceClient.class);
  }

  private void setupWsdl() {
    mailboxServiceMock
      .when(HttpRequest
        .request()
        .withMethod(HttpMethod.GET.toString())
        .withPath("/service/wsdl/ZimbraService.wsdl")
      )
      .respond(HttpResponse.response().withStatusCode(200).withBody(getWsdl()));
  }

  private static String getWsdl() {
    final var path = "schemas/ZimbraService.wsdl";
    try {
      try (var resourceAsStream = MailboxClient.class.getClassLoader().getResourceAsStream(path)) {
        final var bytes = resourceAsStream.readAllBytes();
        return new String(bytes, StandardCharsets.UTF_8);
      }
    } catch (NullPointerException | IOException ex) {
      throw new RuntimeException("Missing Mailbox WSDL resource: " + path, ex);
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
