package cl.huertohogar.catalogo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "jwt.secret-base64=AQIDBAUGBwgJCgsMDQ4PEBESExQVFhcYGRobHB0eHyA=")
@AutoConfigureMockMvc
@ActiveProfiles("demo")
class ProductoControllerTest {
    @Autowired
    MockMvc mvc;

    @Test
    void listaProductosDelCatalogoDemo() throws Exception {
        mvc.perform(get("/api/v1/productos"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].sku").value("BRU-TUB-001"))
            .andExpect(jsonPath("$[0].stock").value(15));
    }

    @Test
    void devuelve404CuandoProductoNoExiste() throws Exception {
        mvc.perform(get("/api/v1/productos/999999"))
            .andExpect(status().isNotFound());
    }
}
