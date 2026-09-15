package com.djmahirnationtv.status.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnableAutoConfiguration(exclude = {
	DataSourceAutoConfiguration.class,
	MongoAutoConfiguration.class
})
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}