package com.krizaka.test.architecture.fixture.exchange.bad;

import com.krizaka.test.architecture.fixture.exchange.bad.domain.Pack;
import java.util.List;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/** Returns a domain type inside a generic. */
@HttpExchange("/packs")
interface LeakingExchange {

  @GetExchange
  List<Pack> packs();
}
