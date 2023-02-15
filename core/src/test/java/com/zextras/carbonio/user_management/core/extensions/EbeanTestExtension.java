// SPDX-FileCopyrightText: 2023 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.core.extensions;

import io.ebean.test.ForTests;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class EbeanTestExtension implements BeforeAllCallback {

  @Override
  public void beforeAll(ExtensionContext context) {
    ForTests.enableTransactional(false);
  }
}
