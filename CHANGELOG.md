# Changelog

## [2.3.0](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v2.2.0...v2.3.0) (2025-11-05)


### Features

* ability to set custom paper size and orientation ([#110](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/110)) ([e5f6364](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/e5f6364be51caeb5d7f09c0ff336e543545bdb9e)), closes [#109](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/109)


### Bug Fixes

* **deps:** update dependency ch.sbb.polarion.extensions:ch.sbb.polarion.extension.generic to v11.3.0 ([88671cd](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/88671cd4236bcbf1caa893265540eeb39843eaa6))
* **deps:** update dependency net.bytebuddy:byte-buddy to v1.17.8 ([20ecb80](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/20ecb80d94b1c6687b55806d2a36e32f11a72d66))
* **deps:** update dependency org.apache.pdfbox:pdfbox to v3.0.6 ([605c39c](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/605c39c7efffae1ca580d3845b4fb11ead999bc8))
* **deps:** update dependency org.testcontainers:testcontainers-bom to v2.0.1 ([43bb1f5](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/43bb1f5845ccc3f318b375b6bfff4670f96e54c4))
* export fails if unresolvable workitem in document ([#106](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/106)) ([dfc822e](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/dfc822ee748ffd621b5e7e5784d64141ee2ee854)), closes [#105](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/105)

## [2.2.0](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v2.1.2...v2.2.0) (2025-10-06)


### Features

* ability to exclude specific document parts/components from rend… ([#97](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/97)) ([d385f33](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/d385f33afd3a7710d663f509cb99231c3d4f1224))
* ability to exclude specific document parts/components from rendering ([d385f33](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/d385f33afd3a7710d663f509cb99231c3d4f1224)), closes [#96](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/96)
* ability to use velocity expressions & special variables in docx templates ([bb42752](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/bb42752f681668254e92efd9f4c12d3d74d2946d)), closes [#93](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/93)
* ability to use velocity expressions & special variables in docx… ([#94](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/94)) ([bb42752](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/bb42752f681668254e92efd9f4c12d3d74d2946d))


### Bug Fixes

* **deps:** update dependency ch.sbb.polarion.extensions:ch.sbb.polarion.extension.generic to v11.1.0 ([511b360](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/511b36020773393d8954b9539311b09813f3c4c9))
* **deps:** update dependency ch.sbb.polarion.extensions:ch.sbb.polarion.extension.generic to v11.2.0 ([8d80d67](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/8d80d677522d416afac9bb99ca24349971dfb25f))
* **deps:** update docx4j.version to v11.5.6 ([6cfd8ea](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/6cfd8eabc7530a182bf4ef78baa57ada21e33dd7))

## [2.1.2](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v2.1.1...v2.1.2) (2025-09-23)


### Bug Fixes

* comment rendering causes a NullPointerException ([#90](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/90)) ([c68eab3](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/c68eab3757bd0beb361532c0f970e88ac3b85d71)), closes [#89](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/89)
* fixed security issue ([#87](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/87)) ([4916cc6](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/4916cc61ec283f43484aef0d29714dafaf4f370f)), closes [#86](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/86)

## [2.1.1](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v2.1.0...v2.1.1) (2025-09-04)


### Bug Fixes

* all export jobs accessible for any user via /jobs endpoints ([#84](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/84)) ([801045b](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/801045bc8256735e79b26c1f12c2d4b683cfd3ce)), closes [#83](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/83)
* **deps:** update dependency net.bytebuddy:byte-buddy to v1.17.7 ([45ff8ae](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/45ff8ae775127a12fd834f0bff98065dfcf85787))
* **deps:** update dependency org.apache.commons:commons-compress to v1.28.0 ([18b6dba](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/18b6dbae37fa130535f8e82e5519c4f8d09183a3))

## [2.1.0](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v2.0.0...v2.1.0) (2025-07-28)


### Features

* added rendering of comments from WI ([#72](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/72)) ([c2bb114](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/c2bb114a1637fc4ac69967356edc8edccd90465b)), closes [#71](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/71)


### Bug Fixes

* **deps:** update dependency ch.sbb.polarion.extensions:ch.sbb.polarion.extension.generic to v11.0.2 ([98613d4](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/98613d43939487d3ed7dad7b789df4c56cadb866))
* fix page breaks (issue introduced by JSoup) ([#75](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/75)) ([48f0695](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/48f0695d06d0a1be51b5b86e65f4023430c226c5)), closes [#74](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/74)
* fix reverting to a template from previous revision ([#68](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/68)) ([454c5be](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/454c5befdd86be10949d343b1afc94c45e4f3a5d))
* fixed links to ToC, ToF, ToT ([#70](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/70)) ([578b434](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/578b434fc11d9f187990ed9c76f0c93f3bbc5200)), closes [#67](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/67)

## [2.0.0](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v1.1.3...v2.0.0) (2025-07-09)


### ⚠ BREAKING CHANGES

* Polarion 2506 support ([#61](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/61))

### Features

* add tests interacting with pandoc-service in docker ([#49](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/49)) ([a6a076f](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/a6a076f258daa3f0ed09aaa9e05eb144e2c702fa)), closes [#33](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/33)
* Polarion 2506 support ([#61](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/61)) ([242493b](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/242493b1e3e9b1b5a94e00a4a3ceb7dad0f77fb4)), closes [#60](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/60)


### Bug Fixes

* **deps:** update dependency ch.sbb.polarion.extensions:ch.sbb.polarion.extension.generic to v10.1.0 ([#53](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/53)) ([50d3154](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/50d31540985036951b386d502fc0b885ddaca8f9))
* **deps:** update dependency ch.sbb.polarion.extensions:ch.sbb.polarion.extension.generic to v9.1.1 ([#51](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/51)) ([3115530](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/311553041b9f5688e60d7875abbaf00596ce2be1))
* **deps:** update dependency net.bytebuddy:byte-buddy to v1.17.6 ([c900bd5](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/c900bd5e5ec605e48bf171dd80ef3a5c62cd2005))
* **deps:** update dependency org.apache.pdfbox:pdfbox to v3.0.5 ([#42](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/42)) ([1d8ff7e](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/1d8ff7ed8b85c93e3909c5e69a8eba915828453b))
* extension registration using bundle activator ([#45](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/45)) ([3c5f629](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/3c5f629f3e3fea2a60045e7f5d3eb56e284731aa))

## [1.1.3](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v1.1.2...v1.1.3) (2025-04-15)


### Bug Fixes

* **deps:** update dependency ch.sbb.polarion.extensions:ch.sbb.polarion.extension.generic to v8.1.1 ([39ece6a](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/39ece6a05a44e6890d751692acc05a7de1c1391b))
* pandoc-service.version ([#36](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/36)) ([e3cfae5](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/e3cfae5e57ae795b44e94bdaf6a564e2b03349b1))

## [1.1.2](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v1.1.1...v1.1.2) (2025-04-15)


### Bug Fixes

* **deps:** update dependency net.bytebuddy:byte-buddy to v1.17.5 ([7633541](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/7633541b4af58f053f541820c3a028239f8887bb))

## [1.1.1](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v1.1.0...v1.1.1) (2025-03-27)


### Bug Fixes

* **deps:** update dependency net.bytebuddy:byte-buddy to v1.17.4 ([#24](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/24)) ([e826ffd](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/e826ffd2044ff81e109dcf1006c55cd9ffa65e59))
* Fix popup JS ([#26](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/26)) ([19edcdc](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/19edcdce6eda01c47824abec8728d2e61b27c7a7))

## [1.1.0](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v1.0.2...v1.1.0) (2025-03-23)


### Features

* ability to provide reference/template file ([#18](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/18)) ([b977a4a](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/b977a4aa6e3fd062386587fa1cf8f0fbc543bc7d)), closes [#17](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/17)
* Native comments rendering ([#21](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/21)) ([fb3cd04](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/fb3cd04ec002005e7cec9c369f86ca52d4dfcda2))


### Bug Fixes

* **deps:** update dependency net.bytebuddy:byte-buddy to v1.17.3 ([b0c26bb](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/b0c26bbe2cf71b0dff189856b22e8e4df049154d))

## [1.0.2](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v1.0.1...v1.0.2) (2025-03-09)


### Bug Fixes

* **deps:** update dependency ch.sbb.polarion.extensions:ch.sbb.polarion.extension.generic to v8.1.0 ([5539922](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/55399220a02a9f12577e055a0e9caf7aceb0bc2d))
* **deps:** update dependency net.bytebuddy:byte-buddy to v1.17.1 ([db29634](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/db29634d73805337a530d79b7ce8ea21029bce3b))
* **deps:** update dependency net.bytebuddy:byte-buddy to v1.17.2 ([425a620](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/425a620943201b581efda0b9c28915c36b2cbcd8))
* generic js modules usage, documentation actualization ([#15](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/15)) ([c9bef6e](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/c9bef6e4a36e23ecefacae6a7804e3dca7003531))
* picking wrong settings issue fix + refactoring ([#13](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/13)) ([e5807f9](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/e5807f95bcb706821cd0d81c0709eb96d1500a6a))

## [1.0.1](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/compare/v1.0.0...v1.0.1) (2025-02-12)


### Bug Fixes

* default Pandoc-service port changed to 9082 ([#9](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/9)) ([ec66a3d](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/ec66a3dc7513ac58820663325308ce4f0d12e47a))
* the page-break in the LiveDoc is not taken into account ([#8](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/8)) ([beb1cae](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/beb1cae671ce3d0950d48d7979a5025318cbb37f))

## 1.0.0 (2025-02-07)


### Features

* initial version ([#3](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/issues/3)) ([af5a598](https://github.com/SchweizerischeBundesbahnen/ch.sbb.polarion.extension.docx-exporter/commit/af5a598be16e4b492d810263eaaf1b36a717fd37))
