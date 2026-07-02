## [1.2.2](https://github.com/zextras/carbonio-user-management/compare/v1.2.1...v1.2.2) (2026-07-02)

## [1.2.1](https://github.com/zextras/carbonio-user-management/compare/v1.2.0...v1.2.1) (2026-06-22)

### Bug Fixes

* units: remove unneeded ReadOnlyPaths ([cc0362e](https://github.com/zextras/carbonio-user-management/commit/cc0362eb4b952ef254a13cca4042cc7d593f5d5b))

## [1.2.0](https://github.com/zextras/carbonio-user-management/compare/v1.1.1...v1.2.0) (2026-06-21)

### Features

* **ci:** [IN-951] add arm64 platform to docker image builds ([#155](https://github.com/zextras/carbonio-user-management/issues/155)) ([9c9f62d](https://github.com/zextras/carbonio-user-management/commit/9c9f62d36e4b1d3c473ace204133f70ab4a0cc2a))

### Bug Fixes

* declare docs license centrally (header-less generated docs) ([#175](https://github.com/zextras/carbonio-user-management/issues/175)) ([1105343](https://github.com/zextras/carbonio-user-management/commit/11053430bf8ae4cd7aff46322018a65e9ecdd4c0))
* derive guest/internal type from isExternalVirtualAccount ([#172](https://github.com/zextras/carbonio-user-management/issues/172)) ([f00307d](https://github.com/zextras/carbonio-user-management/commit/f00307d9ad9b63d64c0c3edb36873b05e4368c3d))

## [1.1.1](https://github.com/zextras/carbonio-user-management/compare/v1.1.0...v1.1.1) (2026-05-27)

### Bug Fixes

* **deps:** add explicit service-discover-base dependency ([#154](https://github.com/zextras/carbonio-user-management/issues/154)) ([f01de4a](https://github.com/zextras/carbonio-user-management/commit/f01de4aada3c3349c522e009841497cb549213d5))

## [1.1.0](https://github.com/zextras/carbonio-user-management/compare/v1.0.3...v1.1.0) (2026-05-04)

### Features

* add bypass_cache flag to all gRPC requests ([#147](https://github.com/zextras/carbonio-user-management/issues/147)) ([f9dd564](https://github.com/zextras/carbonio-user-management/commit/f9dd5647b5f5eb14c7989389d2df86347415a95f))
* adopt carbonio-systemd-notify for native sd_notify readiness ([#136](https://github.com/zextras/carbonio-user-management/issues/136)) ([cabd380](https://github.com/zextras/carbonio-user-management/commit/cabd38007996037e8ea24c1aaa708d6651128d48))
* convert config migration to Java class  ([#131](https://github.com/zextras/carbonio-user-management/issues/131)) ([762da60](https://github.com/zextras/carbonio-user-management/commit/762da60598b8b9cb5c2c5b5888ac627895fdfbae))
* Quarkus refactor with gRPC and internal cache ([#121](https://github.com/zextras/carbonio-user-management/issues/121)) ([324e774](https://github.com/zextras/carbonio-user-management/commit/324e77404e501ee5cbce6ef3f5817369527737f2))
* realistic ITs ([#137](https://github.com/zextras/carbonio-user-management/issues/137)) ([d212aa3](https://github.com/zextras/carbonio-user-management/commit/d212aa3ceedb65e5c983c268044843c1ec89cebf))
* systemd hardening and service-discover.target orchestration ([#132](https://github.com/zextras/carbonio-user-management/issues/132)) ([3dee02c](https://github.com/zextras/carbonio-user-management/commit/3dee02ce2dcb3ece6d6f62bc536ed2f54e206d01))
* use mailbox's internal endpoints instead of soaps ([#140](https://github.com/zextras/carbonio-user-management/issues/140)) ([00432f5](https://github.com/zextras/carbonio-user-management/commit/00432f5ae4ae9801d6d9294c29601860e22bb6f3))

### Bug Fixes

* bump sdk-parent to 1.7.1-1 for .proto inclusion in jar ([#128](https://github.com/zextras/carbonio-user-management/issues/128)) ([32594b9](https://github.com/zextras/carbonio-user-management/commit/32594b96f8ca14d14c9f3c723b35d5911fa6a46a))
* remove submoduleChangelogPaths from Jenkinsfile ([#129](https://github.com/zextras/carbonio-user-management/issues/129)) ([3d25696](https://github.com/zextras/carbonio-user-management/commit/3d25696215553afae5bd75f9af4917031ddac3d6))
* replace ubi9-minimal base image with eclipse-temurin:21-jdk-alpine ([#124](https://github.com/zextras/carbonio-user-management/issues/124)) ([b1f7fae](https://github.com/zextras/carbonio-user-management/commit/b1f7fae1aec5539702ca61bcfe655f750d365019))
* set Consul service protocol to grpc ([#125](https://github.com/zextras/carbonio-user-management/issues/125)) ([6935046](https://github.com/zextras/carbonio-user-management/commit/693504643f0f3dc81ed880f662be38d05ea4da2a))
* sidecar registration using jar ([#123](https://github.com/zextras/carbonio-user-management/issues/123)) ([3ea3d21](https://github.com/zextras/carbonio-user-management/commit/3ea3d210b376c8020161b8bf4cc77141cab80589))
* update Consul health check path to Quarkus endpoint ([#122](https://github.com/zextras/carbonio-user-management/issues/122)) ([d004f6b](https://github.com/zextras/carbonio-user-management/commit/d004f6ba43235534b2bfee3457ab42ed2e23287e))

### Performance Improvements

* [CO-3523] define otel service name and version ([#138](https://github.com/zextras/carbonio-user-management/issues/138)) ([fb3eb92](https://github.com/zextras/carbonio-user-management/commit/fb3eb92a745dbf9c4888bf6d12fefa64abd5ef66))

<!--
SPDX-FileCopyrightText: 2026 2026 Zextras <https://www.zextras.com>

SPDX-License-Identifier: AGPL-3.0-only
-->

## [1.0.2](https://github.com/zextras/carbonio-user-management/compare/v1.0.1...v1.0.2) (2026-02-24)

### Bug Fixes

* **docker:** generate proper config.properties ([#92](https://github.com/zextras/carbonio-user-management/issues/92)) ([51f4549](https://github.com/zextras/carbonio-user-management/commit/51f4549f6e673fb94443468d11061f92acce9cef))
* handle pending status ([#105](https://github.com/zextras/carbonio-user-management/issues/105)) ([c7468aa](https://github.com/zextras/carbonio-user-management/commit/c7468aa1951b10c088f6996167bf3aa98d56c5aa))

## [1.0.1](https://github.com/zextras/carbonio-user-management/compare/v1.0.0...v1.0.1) (2025-11-18)

### Bug Fixes

* let every service call the user myself endpoint ([#90](https://github.com/zextras/carbonio-user-management/issues/90)) ([309cb74](https://github.com/zextras/carbonio-user-management/commit/309cb74fd9e39411fdb573ad120cf4898e29d551))

## [1.0.0](https://github.com/zextras/carbonio-user-management/compare/v0.8.4...v1.0.0) (2025-11-14)

### ⚠ BREAKING CHANGES

* update release config and trigger first major bump (#87)

### Features

* handle carbonio attributes on user myself ([#85](https://github.com/zextras/carbonio-user-management/issues/85)) ([6ea4938](https://github.com/zextras/carbonio-user-management/commit/6ea4938beb49600bc5a4e5e845f5a83145c412a4))

### Bug Fixes

* update release config and trigger first major bump ([#87](https://github.com/zextras/carbonio-user-management/issues/87)) ([0ee3154](https://github.com/zextras/carbonio-user-management/commit/0ee315467c1905060ef33e69b795db59e7b367dd))

## [0.8.4](https://github.com/zextras/carbonio-user-management/compare/v0.8.3...v0.8.4) (2025-10-02)
## [0.8.3](https://github.com/zextras/carbonio-user-management/compare/v0.8.2...v0.8.3) (2025-08-20)

### Features

* build packages from docker ([#77](https://github.com/zextras/carbonio-user-management/issues/77)) ([429eaef](https://github.com/zextras/carbonio-user-management/commit/429eaef99a3b08bf5f061e43ef70b9579b0a1f5f))
* dockerize backend for local testing ([#67](https://github.com/zextras/carbonio-user-management/issues/67)) ([db8bb2f](https://github.com/zextras/carbonio-user-management/commit/db8bb2f76a0145fa453eed591daba36e184c474e))

### Bug Fixes

* include provisioning container for automation ([#70](https://github.com/zextras/carbonio-user-management/issues/70)) ([2505648](https://github.com/zextras/carbonio-user-management/commit/2505648d22e53ca579633d4b1d860e0987a6e185))
* revert WantedBy for compatibility with older systems ([#75](https://github.com/zextras/carbonio-user-management/issues/75)) ([9cb7f8d](https://github.com/zextras/carbonio-user-management/commit/9cb7f8da079071d97d871e1620a8648e899152d0))
## [0.8.2](https://github.com/zextras/carbonio-user-management/compare/v0.8.1...v0.8.2) (2025-05-15)

### Bug Fixes

* add ignore cache to every get method ([#64](https://github.com/zextras/carbonio-user-management/issues/64)) ([16f7d11](https://github.com/zextras/carbonio-user-management/commit/16f7d118c5c95837b60d70f4156674b8ae822599))
* make cache optional in user myself endpoint ([#63](https://github.com/zextras/carbonio-user-management/issues/63)) ([feff614](https://github.com/zextras/carbonio-user-management/commit/feff6145bfe3bfde196a276e3d3fe038901d95f6))
## [0.8.1](https://github.com/zextras/carbonio-user-management/compare/v0.8.0...v0.8.1) (2025-02-03)
## [0.8.0](https://github.com/zextras/carbonio-user-management/compare/v0.7.1...v0.8.0) (2024-11-19)

### Features

* replace health checks from ready to live ([#56](https://github.com/zextras/carbonio-user-management/issues/56)) ([b260b75](https://github.com/zextras/carbonio-user-management/commit/b260b75eb9dfff0cbd6f80f746f433aada76dda3))

### Bug Fixes

* increase the token expiration in the user token cache ([#57](https://github.com/zextras/carbonio-user-management/issues/57)) ([f4d7cb2](https://github.com/zextras/carbonio-user-management/commit/f4d7cb2e4ef375fc9c61bb1da50c15bb7e15a404))
## [0.7.1](https://github.com/zextras/carbonio-user-management/compare/v0.7.0...v0.7.1) (2024-09-10)

### Features

* add ubuntu 24.04 (ubuntu-noble) support ([b85a491](https://github.com/zextras/carbonio-user-management/commit/b85a491597a9f84f4cf3a1c83baceda706c7dff8))
## [0.7.0](https://github.com/zextras/carbonio-user-management/compare/v0.6.0...v0.7.0) (2024-08-14)

### Features

* add cache for getUserMyself response ([#48](https://github.com/zextras/carbonio-user-management/issues/48)) ([cbc4f0c](https://github.com/zextras/carbonio-user-management/commit/cbc4f0c7854884d6232f8b2db93a6017bfc9ad57))

### Bug Fixes

* move jar from /usr/bin to /usr/share to follow the FHS standard ([#47](https://github.com/zextras/carbonio-user-management/issues/47)) ([06a933c](https://github.com/zextras/carbonio-user-management/commit/06a933c75b72a79fe156a41bbbefead97c41cd0f))
## [0.6.0](https://github.com/zextras/carbonio-user-management/compare/v0.5.2...v0.6.0) (2024-06-17)

### Features

* add intention to call myself user from files ([#43](https://github.com/zextras/carbonio-user-management/issues/43)) ([0ceb876](https://github.com/zextras/carbonio-user-management/commit/0ceb8761ef0099a6c1ab732e666c0483d470768c))
* return the account type with GetMySelf, GetUserById and GetUserByEmail and adapt tests ([#44](https://github.com/zextras/carbonio-user-management/issues/44)) ([17f56b5](https://github.com/zextras/carbonio-user-management/commit/17f56b5a996d92a666e703de54e6b4ec25665567))
* status is now returned when getting user info ([#41](https://github.com/zextras/carbonio-user-management/issues/41)) ([605d080](https://github.com/zextras/carbonio-user-management/commit/605d08014c7ec59bcdcda38e9d0dfab8862aec95))
## [0.5.2](https://github.com/zextras/carbonio-user-management/compare/v0.5.1...v0.5.2) (2024-04-12)
## [0.5.1](https://github.com/zextras/carbonio-user-management/compare/v0.5.0...v0.5.1) (2024-02-15)

### Bug Fixes

* *.hcl: apply corrections to validate with hclfmt ([#36](https://github.com/zextras/carbonio-user-management/issues/36)) ([2b830ff](https://github.com/zextras/carbonio-user-management/commit/2b830fffd073d63c51491b28545550326d823da0))
## [0.5.0](https://github.com/zextras/carbonio-user-management/compare/v0.4.0...v0.5.0) (2023-11-24)

### Features

* move to yap agent and add rhel9 support ([#33](https://github.com/zextras/carbonio-user-management/issues/33)) ([bc67379](https://github.com/zextras/carbonio-user-management/commit/bc67379f54c01e2b759cab9b9302f6d9001b75c3))
* replace soapclient with carbonio-mailbox-sdk ([#32](https://github.com/zextras/carbonio-user-management/issues/32)) ([0bc2ec5](https://github.com/zextras/carbonio-user-management/commit/0bc2ec5b11d72dac1fd3459fd57f3683cb7a2292))
## [0.4.0](https://github.com/zextras/carbonio-user-management/compare/v0.3.0...v0.4.0) (2023-10-30)

### ⚠ BREAKING CHANGES

* The /users/myself API response is changed to follow the
different format used to represent the locale value. Now it is a string
(instead of an enumerator) and it can support all the values defined in
the standard.

refs: UM-25

### Bug Fixes

* change the format of a returned account locale to xx_YY ([#30](https://github.com/zextras/carbonio-user-management/issues/30)) ([c7065ca](https://github.com/zextras/carbonio-user-management/commit/c7065ca645721ed48ffa9fe4e64ebfcde602cd92))
## [0.3.0](https://github.com/zextras/carbonio-user-management/compare/v0.2.4...v0.3.0) (2023-08-31)

### Features

* implement /users/myself API to expose the user locale prefs ([#25](https://github.com/zextras/carbonio-user-management/issues/25)) ([a24b7c0](https://github.com/zextras/carbonio-user-management/commit/a24b7c02e5b87062da40c7fce97449948b837fc9)), closes [SoapClient#getAccountInfoByAuthToken](https://github.com/zextras/SoapClient/issues/getAccountInfoByAuthToken)
## [0.2.4](https://github.com/zextras/carbonio-user-management/compare/v0.2.3...v0.2.4) (2023-07-06)
## [0.2.3](https://github.com/zextras/carbonio-user-management/compare/v0.2.2...v0.2.3) (2023-05-25)
## [0.2.2](https://github.com/zextras/carbonio-user-management/compare/v0.2.1...v0.2.2) (2023-04-27)
## [0.2.1](https://github.com/zextras/carbonio-user-management/compare/v0.2.0...v0.2.1) (2023-03-30)

### Bug Fixes

* UM14 fix GetUsers API returning a list of UserInfo ([#12](https://github.com/zextras/carbonio-user-management/issues/12)) ([ee5ed55](https://github.com/zextras/carbonio-user-management/commit/ee5ed556043ab5c514b67c761f9f7c85991424e5))
## [0.2.0](https://github.com/zextras/carbonio-user-management/compare/v0.1.3...v0.2.0) (2023-03-28)

### Features

* UM-13 add intentions for carbonio-tasks ([#9](https://github.com/zextras/carbonio-user-management/issues/9)) ([16de1b9](https://github.com/zextras/carbonio-user-management/commit/16de1b924d9c4f26e34a2c568194adf67a3b85c2))

### Bug Fixes

* **backstage:** Entity reference had missing kind ([#7](https://github.com/zextras/carbonio-user-management/issues/7)) ([f54800e](https://github.com/zextras/carbonio-user-management/commit/f54800e70c8e2e37c3e812993c186df227afae9a))
## [0.1.3](https://github.com/zextras/carbonio-user-management/compare/v0.1.2...v0.1.3) (2022-11-24)
## [0.1.2](https://github.com/zextras/carbonio-user-management/compare/v0.1.1...v0.1.2) (2022-09-09)
## [0.1.1](https://github.com/zextras/carbonio-user-management/compare/4f3b9aa1afe3a998d4792159169b2ad19e8c615e...v0.1.1) (2022-03-23)

### Features

* carbonio release ([4f3b9aa](https://github.com/zextras/carbonio-user-management/commit/4f3b9aa1afe3a998d4792159169b2ad19e8c615e))
