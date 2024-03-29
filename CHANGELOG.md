<!--
SPDX-FileCopyrightText: 2022 Zextras <https://www.zextras.com>

SPDX-License-Identifier: AGPL-3.0-only
-->

# Changelog

All notable changes to this project will be documented in this file. See [standard-version](https://github.com/conventional-changelog/standard-version) for commit guidelines.

### [0.5.1](https://github.com/Zextras/carbonio-user-management/compare/v0.5.0...v0.5.1) (2024-02-15)

### Bug Fixes

* *.hcl: apply corrections to validate with hclfmt ([#36](https://github.com/Zextras/carbonio-user-management/issues/36)) ([2b830ff](https://github.com/Zextras/carbonio-user-management/commit/2b830fffd073d63c51491b28545550326d823da0))

## [0.5.0](https://github.com/Zextras/carbonio-user-management/compare/v0.4.0...v0.5.0) (2023-11-24)

### Features

* move to yap agent and add rhel9 support ([#33](https://github.com/Zextras/carbonio-user-management/issues/33)) ([bc67379](https://github.com/Zextras/carbonio-user-management/commit/bc67379f54c01e2b759cab9b9302f6d9001b75c3))
* replace soapclient with carbonio-mailbox-sdk ([#32](https://github.com/Zextras/carbonio-user-management/issues/32)) ([0bc2ec5](https://github.com/Zextras/carbonio-user-management/commit/0bc2ec5b11d72dac1fd3459fd57f3683cb7a2292))

## [0.4.0](https://github.com/Zextras/carbonio-user-management/compare/v0.3.0...v0.4.0) (2023-10-30)

### ⚠ BREAKING CHANGES

* The /users/myself API response is changed to follow the
different format used to represent the locale value. Now it is a string
(instead of an enumerator) and it can support all the values defined in
the standard.

refs: UM-25

### Bug Fixes

* change the format of a returned account locale to xx_YY ([#30](https://github.com/Zextras/carbonio-user-management/issues/30)) ([c7065ca](https://github.com/Zextras/carbonio-user-management/commit/c7065ca645721ed48ffa9fe4e64ebfcde602cd92))

## [0.3.0](https://github.com/Zextras/carbonio-user-management/compare/v0.2.4...v0.3.0) (2023-08-31)

### Features

* implement /users/myself API to expose the user locale prefs ([#25](https://github.com/Zextras/carbonio-user-management/issues/25)) ([a24b7c0](https://github.com/Zextras/carbonio-user-management/commit/a24b7c02e5b87062da40c7fce97449948b837fc9))

### [0.2.4](https://github.com/Zextras/carbonio-user-management/compare/v0.2.3...v0.2.4) (2023-07-06)

### [0.2.3](https://github.com/Zextras/carbonio-user-management/compare/v0.2.2...v0.2.3) (2023-05-25)

### [0.2.2](https://github.com/Zextras/carbonio-user-management/compare/v0.2.1...v0.2.2) (2023-04-27)

### [0.2.1](https://github.com/Zextras/carbonio-user-management/compare/v0.2.0...v0.2.1) (2023-03-30)

### Bug Fixes

* UM14 fix GetUsers API returning a list of UserInfo ([#12](https://github.com/Zextras/carbonio-user-management/issues/12)) ([ee5ed55](https://github.com/Zextras/carbonio-user-management/commit/ee5ed556043ab5c514b67c761f9f7c85991424e5))

### [0.2.0](https://github.com/Zextras/carbonio-user-management/compare/v0.1.3...v0.2.0) (2023-03-28)

### Features

* UM-13 add intentions for carbonio-tasks ([#9](https://github.com/Zextras/carbonio-user-management/issues/9)) ([16de1b9](https://github.com/Zextras/carbonio-user-management/commit/16de1b924d9c4f26e34a2c568194adf67a3b85c2))

* UM-11 add a service to retrieve a list of users ([#10](https://github.com/Zextras/carbonio-user-management/issues/10)) ([0afd9cb](https://github.com/Zextras/carbonio-user-management/commit/0afd9cbb57ee868bd17b15deee9ee5e0841c0030))

### Bug Fixes

* **backstage:** Entity reference had missing kind ([#7](https://github.com/Zextras/carbonio-user-management/issues/7)) ([f54800e](https://github.com/Zextras/carbonio-user-management/commit/f54800e70c8e2e37c3e812993c186df227afae9a))

### [0.1.3](https://github.com/Zextras/carbonio-user-management/compare/v0.1.2...v0.1.3) (2022-11-24)

### [0.1.2](https://github.com/Zextras/carbonio-user-management/compare/v0.1.1...v0.1.2) (2022-09-09)

## [v0.1.1]() (2022-03-24)

### Features

* carbonio release (4f3b9aa)
