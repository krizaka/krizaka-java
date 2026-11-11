package com.krizaka.test.architecture.fixture.exchange.good;

import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Package-private, wire records only. */
@HttpExchange("/packs")
interface PackExchange {

  @GetExchange("/{key}")
  PackBody pack(String key);
}
