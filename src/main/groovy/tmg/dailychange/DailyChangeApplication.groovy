package tmg.dailychange

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class DailyChangeApplication {

	static void main(String[] args) {
		SpringApplication.run(DailyChangeApplication, args)
	}

	@Autowired
	DailyChangeApplication(FileImportService fileImportService) {
		fileImportService.load()
	}
}
