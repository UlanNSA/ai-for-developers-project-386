# Changelog

## [0.3.0](https://github.com/UlanNSA/ai-for-developers-project-386/compare/v0.2.0...v0.3.0) (2026-11-02)


### Features

* **backend:** serve the built frontend from STATIC_DIR ([26dfffb](https://github.com/UlanNSA/ai-for-developers-project-386/commit/26dfffb4485c4b71d4c4ce89d2c658297ebc4f14)), closes [#34](https://github.com/UlanNSA/ai-for-developers-project-386/issues/34)
* Docker image, Render Blueprint and smoke script ([1acf9f2](https://github.com/UlanNSA/ai-for-developers-project-386/commit/1acf9f2cdb34d8c88dddb871501cd5d0395c372e)), closes [#34](https://github.com/UlanNSA/ai-for-developers-project-386/issues/34)

## [0.2.0](https://github.com/UlanNSA/ai-for-developers-project-386/compare/v0.1.0...v0.2.0) (2026-11-02)


### Features

* add GET /api/owner through the whole contract chain ([114d9b3](https://github.com/UlanNSA/ai-for-developers-project-386/commit/114d9b32405df5e65b51ec9a37650e4cb00e1485))
* **api,backend:** create bookings atomically with POST /api/bookings ([cac3f17](https://github.com/UlanNSA/ai-for-developers-project-386/commit/cac3f1701e20de4468c718619bf001cf3ddca4d3)), closes [#24](https://github.com/UlanNSA/ai-for-developers-project-386/issues/24)
* **api,backend:** create meeting types through POST /api/meeting-types ([5538a03](https://github.com/UlanNSA/ai-for-developers-project-386/commit/5538a035d6d9c760d5037047d845925e9c55ce88)), closes [#22](https://github.com/UlanNSA/ai-for-developers-project-386/issues/22)
* **api,backend:** list upcoming bookings with GET /api/bookings ([ead7ddd](https://github.com/UlanNSA/ai-for-developers-project-386/commit/ead7ddda2b1fe90af74384ea3b63202df26fdd45)), closes [#25](https://github.com/UlanNSA/ai-for-developers-project-386/issues/25)
* **api,backend:** serve meeting types through GET /api/meeting-types ([1520272](https://github.com/UlanNSA/ai-for-developers-project-386/commit/152027253855c5c0e94fdbaa44656014820039cc)), closes [#20](https://github.com/UlanNSA/ai-for-developers-project-386/issues/20)
* **backend:** POST /__test__/clock moves the fixed clock in test mode ([0643220](https://github.com/UlanNSA/ai-for-developers-project-386/commit/064322069f8e4582484b0a25e09a0adb7cc7a189)), closes [#28](https://github.com/UlanNSA/ai-for-developers-project-386/issues/28)
* **backend:** test mode with fixed clock and reset route behind CALENDAR_TEST_MODE ([49be0da](https://github.com/UlanNSA/ai-for-developers-project-386/commit/49be0dab55267ac2c2e01dbccefac8ca612b25b9)), closes [#26](https://github.com/UlanNSA/ai-for-developers-project-386/issues/26)
* calculate slots from the schedule and let guests pick a day and time ([d91160a](https://github.com/UlanNSA/ai-for-developers-project-386/commit/d91160adf64faba2e6ebd672681b2fb920ea916a)), closes [#23](https://github.com/UlanNSA/ai-for-developers-project-386/issues/23)
* **e2e:** Playwright package, smoke test, mise tasks and e2e CI job ([6f6d95a](https://github.com/UlanNSA/ai-for-developers-project-386/commit/6f6d95a791a0ccf6924b23e966e17620afab7a5a)), closes [#26](https://github.com/UlanNSA/ai-for-developers-project-386/issues/26)
* **frontend:** add the /owner page to create meeting types ([e8d30d9](https://github.com/UlanNSA/ai-for-developers-project-386/commit/e8d30d9e4e01221107d850c2fb0d280225444cd7)), closes [#22](https://github.com/UlanNSA/ai-for-developers-project-386/issues/22)
* **frontend:** booking form, confirmation and conflict handling ([7fb785e](https://github.com/UlanNSA/ai-for-developers-project-386/commit/7fb785e1f70758ef04b9839977dd01cb198dd8e7)), closes [#24](https://github.com/UlanNSA/ai-for-developers-project-386/issues/24)
* **frontend:** load meeting types from the API and drop static data ([a73fe81](https://github.com/UlanNSA/ai-for-developers-project-386/commit/a73fe81f806ae8fde473ee603c70c170ea212cfa)), closes [#20](https://github.com/UlanNSA/ai-for-developers-project-386/issues/20)
* **frontend:** upcoming bookings block on the owner page ([29f312a](https://github.com/UlanNSA/ai-for-developers-project-386/commit/29f312ad981cab0a1251cf42334991a8b00496cf)), closes [#25](https://github.com/UlanNSA/ai-for-developers-project-386/issues/25)


### Bug Fixes

* **backend,e2e:** require X-Calendar-Test header on test-mode routes ([3ffa6c4](https://github.com/UlanNSA/ai-for-developers-project-386/commit/3ffa6c4364cc4ba47835cb9ea6f25408fb2b9d09)), closes [#16](https://github.com/UlanNSA/ai-for-developers-project-386/issues/16)
* **backend:** validate emails and meeting type ids strictly ([4760daa](https://github.com/UlanNSA/ai-for-developers-project-386/commit/4760daa650b00fc8891d9088bf6297f754b9d136)), closes [#16](https://github.com/UlanNSA/ai-for-developers-project-386/issues/16)
* document 500 ApiError for getOwner and type owner test data against the SDK ([414e70b](https://github.com/UlanNSA/ai-for-developers-project-386/commit/414e70b7fad51334ff5a384bdaf0cafa7d3a19ce))
* **frontend:** reset the selected day and slot when the slots reload ([78e3f0e](https://github.com/UlanNSA/ai-for-developers-project-386/commit/78e3f0e184c720900224aa5f87981632c678538f)), closes [#16](https://github.com/UlanNSA/ai-for-developers-project-386/issues/16)
