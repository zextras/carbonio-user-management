// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

/**
 * Utility class to offer methods for generating SOAP request/response by substituting input values
 * and expected values to request/response XML files.
 */
public class SoapHttpUtils {

  /**
   * Creates the SoapNotFoundError XML body.
   *
   * @return a {@link String} representing the XML body response containing the NotFound error.
   */
  public String getSoapNotFoundErrorResponse() {
    return getXmlFile("soap/responses/SoapNotFoundError.xml");
  }

  /**
   * Creates the GetAccountInfoRequest XML body substituting the auth token and the account id in
   * the related placeholders.
   *
   * @param authToken is a {@links String} representing the auth token of the requester
   * @param accountId is a {@link String} representing the identifier of the account to retrieve
   * @return a {@link String} representing the XML body request for the GetAccountInfo API.
   */
  public String getAccountInfoRequest(String authToken, String accountId) {
    String getAccountInfoRequest = getXmlFile("soap/requests/GetAccountInfoRequest.xml");

    return getAccountInfoRequest
      .replaceAll("%AUTH_TOKEN%", authToken)
      .replaceAll("%ACCOUNT_ID%", accountId);
  }

  /**
   * Creates the GetAccountInfoResponse XML body substituting the input parameters in the related
   * placeholders.
   *
   * @param accountId       is a {@link String} representing the identifier of the retrieved
   *                        account
   * @param accountEmail    is a {@link String} representing the email of the retrieved account
   * @param accountDomain   is a {@link String} representing the domain of the retrieved account
   * @param accountFullName is a {@link String} representing the full name of the retrieved account
   * @return a {@link String} representing the XML payload response for the GetAccountInfo API.
   */
  public String getAccountInfoResponse(String accountId, String accountEmail, String accountDomain,
    String accountFullName) {
    String getAccountInfoResponse = getXmlFile("soap/responses/GetAccountInfoResponse.xml");

    return getAccountInfoResponse
      .replaceAll("%ACCOUNT_ID%", accountId)
      .replaceAll("%ACCOUNT_EMAIL%", accountEmail)
      .replaceAll("%ACCOUNT_DOMAIN%", accountDomain)
      .replaceAll("%ACCOUNT_FULL_NAME%", accountFullName);
  }

  /**
   * Creates the GetInfoRequest XML body substituting the auth token in the related placeholders.
   *
   * @param authToken is a {@links String} representing the auth token of the requester
   * @return a {@link String} representing the XML body request for the GetInfo API.
   */
  public String getInfoRequest(String authToken) {
    String getInfoRequest = getXmlFile("soap/requests/GetInfoRequest.xml");

    return getInfoRequest.replaceAll("%AUTH_TOKEN%", authToken);
  }

  /**
   * Creates the GetInfoResponse XML body substituting the input parameters in the related
   * placeholders.
   *
   * @param accountId       is a {@link String} representing the identifier of the retrieved
   *                        account
   * @param accountEmail    is a {@link String} representing the email of the retrieved account
   * @param accountDomain   is a {@link String} representing the domain of the retrieved account
   * @param accountFullName is a {@link String} representing the full name of the retrieved account
   * @param accountLocale   is a {@link String} representing the locale chosen by the retrieved
   *                        account
   * @return a {@link String} representing the XML payload response for the GetInfo API.
   */
  public String getInfoResponse(
    String accountId,
    String accountEmail,
    String accountDomain,
    String accountFullName,
    String accountLocale
  ) {
    String getInfoResponse = getXmlFile("soap/responses/GetInfoResponse.xml");

    return getInfoResponse
      .replaceAll("%ACCOUNT_ID%", accountId)
      .replaceAll("%ACCOUNT_EMAIL%", accountEmail)
      .replaceAll("%ACCOUNT_DOMAIN%", accountDomain)
      .replaceAll("%ACCOUNT_FULL_NAME%", accountFullName)
      .replaceAll("%ACCOUNT_LOCALE%", accountLocale);
  }

  /**
   * Allows to load the request/response xml file in memory to performs some substitution.
   *
   * @param path is {@link String} representing the path with the filename of the file to open
   * @return a {@link String} containing the content of the opened file without indentation.
   */
  private String getXmlFile(String path) {

    try (InputStream resource = getClass().getClassLoader().getResourceAsStream(path)) {
      return IOUtils
        .toString(resource, StandardCharsets.UTF_8)
        .replaceAll("\n( *)<", "<"); // This replacement is necessary to remove the XML indentation;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
