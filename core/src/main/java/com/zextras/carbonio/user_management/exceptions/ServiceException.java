// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.exceptions;

/**
 * Exception to be used when something bad happen during the execution of a request or when a
 * dependency (like the carbonio-mailbox) responds with an unexpected/unhandled error.
 */
public class ServiceException extends RuntimeException {

  public ServiceException(String message) {
    super(message);
  }

  public ServiceException(String message, Throwable throwable) {
    super(message, throwable);
  }
}
