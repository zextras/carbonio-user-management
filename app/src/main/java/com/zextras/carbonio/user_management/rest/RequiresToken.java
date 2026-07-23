// SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>
//
// SPDX-License-Identifier: AGPL-3.0-only

package com.zextras.carbonio.user_management.rest;

import jakarta.ws.rs.NameBinding;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks the one REST endpoint ({@code myself}) that needs the auth token. The token is not an
 * authorization gate here: it is the functional identity input that {@code getUserMyself}
 * forwards to mailbox to resolve "who am I". {@link TokenAuthFilter} is bound to this
 * annotation and extracts the token from the {@code ZM_AUTH_TOKEN} cookie for the endpoints
 * that carry it, aborting with 401 if the cookie is missing or blank.
 *
 * <p>Endpoints without this annotation (by-id, by-email, and the batch lookup) are trusted
 * forwards to mailbox's internal REST API: mailbox handles service-to-service authorization via
 * the Consul mesh, so these lookups neither require nor read any token.
 */
@NameBinding
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequiresToken {}
