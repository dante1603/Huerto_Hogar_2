package cl.huertohogar.catalogo.config;

import cl.huertohogar.catalogo.domain.Producto;
import cl.huertohogar.catalogo.persistence.ProductoRepository;
import java.math.BigDecimal;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("demo")
public class DemoCatalogo {
    @Bean
    CommandLineRunner productosDeEjemplo(ProductoRepository repository) {
        return args -> {
            if (repository.count() != 0) return;
            repository.save(new Producto(
                "BRU-TUB-001", "Tuberculo de Bruma",
                "Cultivo de raiz del planeta Bruma",
                new BigDecimal("120"), 15, "Bruma"));
            repository.save(new Producto(
                "ARI-FRU-002", "Fruta de Oasis",
                "Fruta ficticia de los oasis de Arido",
                new BigDecimal("240"), 8, "Arido"));
            repository.save(new Producto(
                "YER-SEM-003", "Semillas del Yermo",
                "Semillas de prueba para cultivos experimentales",
                new BigDecimal("90"), 25, "Yermo"));
        };
    }
}
