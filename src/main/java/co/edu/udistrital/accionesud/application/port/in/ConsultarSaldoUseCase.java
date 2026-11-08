package co.edu.udistrital.accionesud.application.port.in;

import co.edu.udistrital.accionesud.domain.model.Saldo;

/**
 * Puerto de entrada: consultar el saldo (disponible y reservado) de una cuenta.
 */
public interface ConsultarSaldoUseCase {

    /**
     * Devuelve el saldo del usuario. Si el usuario es nuevo, retorna el saldo
     * inicial simulado configurado para la plataforma.
     *
     * @param usuarioId identificador del usuario.
     * @return saldo de la cuenta.
     */
    Saldo consultarSaldo(String usuarioId);
}
