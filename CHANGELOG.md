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
