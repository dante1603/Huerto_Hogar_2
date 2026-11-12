package com.example.demo.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario", uniqueConstraints = {
    @UniqueConstraint(name = "uk_usuario_rut", columnNames = "rut_usuario"),
    @UniqueConstraint(name = "uk_usuario_email", columnNames = "email_usuario")
})
public class Usuario {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Long idUsuario;

    @Column(name = "nombre_usuario", nullable = false, length = 120)
    private String nombreUsuario;

    @Column(name = "rut_usuario", nullable = false, length = 12)
    private String rutUsuario;

    @Column(name = "direccion_usuario", nullable = false, length = 240)
    private String direccionUsuario;

    @Column(name = "email_usuario", nullable = false, length = 180)
    private String emailUsuario;

    // Nunca guardar ni exponer la contrasena en texto plano.
    @Column(name = "contrasena_usuario", nullable = false, length = 100)
    private String contrasenaHash;

    @Column(name = "fecha_hora_creacion_usuario", nullable = false, updatable = false)
    private LocalDateTime fechaHoraCreacionUsuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol_usuario", nullable = false, length = 20)
    private RolUsuario rolUsuario = RolUsuario.CLIENTE;

    @PrePersist
    void inicializarFecha() {
        if (fechaHoraCreacionUsuario == null) {
            fechaHoraCreacionUsuario = LocalDateTime.now();
        }
    }

    public Long getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Long idUsuario) { this.idUsuario = idUsuario; }
    public String getNombreUsuario() { return nombreUsuario; }
    public void setNombreUsuario(String nombreUsuario) { this.nombreUsuario = nombreUsuario; }
    public String getRutUsuario() { return rutUsuario; }
    public void setRutUsuario(String rutUsuario) { this.rutUsuario = rutUsuario; }
    public String getDireccionUsuario() { return direccionUsuario; }
    public void setDireccionUsuario(String direccionUsuario) { this.direccionUsuario = direccionUsuario; }
    public String getEmailUsuario() { return emailUsuario; }
    public void setEmailUsuario(String emailUsuario) { this.emailUsuario = emailUsuario; }
    public String getContrasenaHash() { return contrasenaHash; }
    public void setContrasenaHash(String contrasenaHash) { this.contrasenaHash = contrasenaHash; }
    public LocalDateTime getFechaHoraCreacionUsuario() { return fechaHoraCreacionUsuario; }
    public void setFechaHoraCreacionUsuario(LocalDateTime value) { this.fechaHoraCreacionUsuario = value; }
    public RolUsuario getRolUsuario() { return rolUsuario; }
    public void setRolUsuario(RolUsuario rolUsuario) { this.rolUsuario = rolUsuario; }
}
