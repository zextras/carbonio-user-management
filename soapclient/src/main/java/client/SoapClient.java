// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package client;

import com.sun.xml.ws.api.message.Header;
import com.sun.xml.ws.api.message.Headers;
import com.sun.xml.ws.developer.WSBindingProvider;
import com.sun.xml.ws.fault.ServerSOAPFaultException;
import https.www_zextras_com.wsdl.zimbraservice.ZcsPortType;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.Service;
import org.w3c.dom.Document;
import zimbra.AccountBy;
import zimbra.AccountSelector;
import zimbra.AuthTokenControl;
import zimbra.HeaderContext;
import zimbra.ObjectFactory;
import zimbraaccount.GetAccountInfoRequest;
import zimbraaccount.GetAccountInfoResponse;
import zimbraaccount.GetInfoRequest;
import zimbraaccount.GetInfoResponse;

public class SoapClient {

  private static ConcurrentLinkedQueue<ZcsPortType> zimbraServiceQueue = new ConcurrentLinkedQueue<>();

  private final HeaderContext soapHeaderContext;

  private SoapClient() {
    AuthTokenControl tokenControl = new AuthTokenControl();
    tokenControl.setVoidOnExpired(true);
    soapHeaderContext = new HeaderContext();
    soapHeaderContext.setAuthTokenControl(tokenControl);
  }

  public static void init(String mailboxUrl) {
    try {
      QName qName = new QName("https://www.zextras.com/wsdl/ZimbraService.wsdl", "zcsService");
      URL zimbraEndpoint = new URL(mailboxUrl + "/service/wsdl/ZimbraService.wsdl");
      IntStream.range(0, 5).forEach(i -> {
        ZcsPortType service = Service
          .create(zimbraEndpoint, qName)
          .getPort(ZcsPortType.class);

        WSBindingProvider provider = ((WSBindingProvider) service);
        provider.setAddress(mailboxUrl + "/service/soap/");

        zimbraServiceQueue.add(service);
      });
    } catch (MalformedURLException e) {
      e.printStackTrace();
    }
  }

  public static SoapClient newClient() {
    return new SoapClient();
  }

  private ZcsPortType getZimbraService() {
    ZcsPortType service = null;
    while (service == null) {
      service = zimbraServiceQueue.poll();
    }
    return service;
  }

  public SoapClient setAuthToken(String authToken) {
    soapHeaderContext.setAuthToken(authToken);
    return this;
  }

  private Header createServiceSoapHeader() throws ParserConfigurationException, JAXBException {
    // Create a SOAP header marshalling the actual header (JAXBElement) into a W3C Document
    Document xmlDocumentHeader = DocumentBuilderFactory
      .newInstance()
      .newDocumentBuilder()
      .newDocument();

    JAXBElement<HeaderContext> jaxbElementHeader =
      new ObjectFactory().createContext(soapHeaderContext);

    JAXBContext
      .newInstance(HeaderContext.class)
      .createMarshaller()
      .marshal(jaxbElementHeader, xmlDocumentHeader);

    return Headers.create(xmlDocumentHeader.getDocumentElement());
  }

  public GetAccountInfoResponse getAccountInfoById(UUID accountUuid)
    throws JAXBException, ParserConfigurationException, ServerSOAPFaultException {

    GetAccountInfoRequest accountInfoRequest = new GetAccountInfoRequest();
    AccountSelector accountSelector = new AccountSelector();
    accountSelector.setBy(AccountBy.ID);
    accountSelector.setValue(accountUuid.toString());
    accountInfoRequest.setAccount(accountSelector);

    ZcsPortType zimbraService = getZimbraService();

    try {
      ((WSBindingProvider) zimbraService).setOutboundHeaders(createServiceSoapHeader());
      return zimbraService.getAccountInfoRequest(accountInfoRequest, soapHeaderContext);
    } finally {
      zimbraServiceQueue.add(zimbraService);
    }
  }

  public GetAccountInfoResponse getAccountInfoByEmail(String accountEmail)
    throws JAXBException, ParserConfigurationException, ServerSOAPFaultException {

    GetAccountInfoRequest accountInfoRequest = new GetAccountInfoRequest();
    AccountSelector accountSelector = new AccountSelector();
    accountSelector.setBy(AccountBy.NAME);
    accountSelector.setValue(accountEmail);
    accountInfoRequest.setAccount(accountSelector);

    ZcsPortType zimbraService = getZimbraService();

    try {
      ((WSBindingProvider) zimbraService).setOutboundHeaders(createServiceSoapHeader());
      return zimbraService.getAccountInfoRequest(accountInfoRequest, soapHeaderContext);
    } finally {
      zimbraServiceQueue.add(zimbraService);
    }
  }

  public GetInfoResponse validateAuthToken()
    throws JAXBException, ParserConfigurationException, ServerSOAPFaultException {

    GetInfoRequest getInfoRequest = new GetInfoRequest();
    getInfoRequest.setSections("children");

    ZcsPortType zimbraService = getZimbraService();

    try {
      WSBindingProvider provider = ((WSBindingProvider) zimbraService);
      provider.setOutboundHeaders(createServiceSoapHeader());
      return zimbraService.getInfoRequest(getInfoRequest, soapHeaderContext);
    } finally {
      zimbraServiceQueue.add(zimbraService);
    }
  }
}
