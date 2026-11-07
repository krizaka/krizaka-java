package com.krizaka.test.architecture.fixture.exchange.bad;

import org.springframework.web.service.annotation.HttpExchange;

/** Public: every package can reach the transport. */
@HttpExchange("/packs")
public interface PublicExchange {}
