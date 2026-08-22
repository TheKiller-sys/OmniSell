package com.omniventas.app.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface VentaDao {
    @Insert
    long insert(VentaEntity venta);

    @Update
    void update(VentaEntity venta);

    @Query("SELECT * FROM ventas_pendientes WHERE id = :id")
    VentaEntity getById(long id);

    @Query("SELECT * FROM ventas_pendientes WHERE sincronizado = 0 ORDER BY fecha ASC")
    List<VentaEntity> getPendientes();

    @Query("SELECT * FROM ventas_pendientes WHERE sincronizado = 1 ORDER BY fecha DESC LIMIT 50")
    List<VentaEntity> getSincronizadas();

    @Query("SELECT COUNT(*) FROM ventas_pendientes WHERE sincronizado = 0")
    int getPendientesCount();

    @Query("DELETE FROM ventas_pendientes WHERE sincronizado = 1")
    void deleteSincronizadas();

    @Query("UPDATE ventas_pendientes SET sincronizado = 1 WHERE id = :id")
    void marcarSincronizada(long id);

    @Query("UPDATE ventas_pendientes SET error = :error WHERE id = :id")
    void setError(long id, String error);
}
