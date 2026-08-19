package com.omniventas.app.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ProductoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ProductoEntity> productos);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(ProductoEntity producto);

    @Update
    void update(ProductoEntity producto);

    @Query("SELECT * FROM productos WHERE isDeleted = 0 ORDER BY nombre ASC")
    List<ProductoEntity> getAll();

    @Query("SELECT * FROM productos WHERE id = :id AND isDeleted = 0")
    ProductoEntity getById(int id);

    @Query("SELECT * FROM productos WHERE nombre LIKE '%' || :query || '%' AND isDeleted = 0")
    List<ProductoEntity> search(String query);

    @Query("DELETE FROM productos")
    void deleteAll();

    @Query("UPDATE productos SET isDeleted = 1 WHERE id = :id")
    void softDelete(int id);
}
