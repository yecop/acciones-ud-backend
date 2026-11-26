package co.edu.udistrital.accionesud.shared.infrastructure.adapter.in.rest.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DetalleErrorTest {

    @Test
    void normalizaIndicesDeColeccionesAlNombreDelCampo() {
        assertThat(DetalleError.campo("canales[]", "no puede ser null").campo()).isEqualTo("canales");
        assertThat(DetalleError.campo("mercados[2]", "valor inválido").campo()).isEqualTo("mercados");
    }
}
