package cm.uy1.campusconnect.auth_service;

import org.springframework.boot.SpringApplication;

public class TestAuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(AuthServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
