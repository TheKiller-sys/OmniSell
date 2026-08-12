# models/vendor.py - Modelo para vendedores
import random
import string

class Vendor:
    """Modelo para vendedores"""
    
    def __init__(self, id=None, name=None, business_id=None, role='vendedor', active=True):
        self.id = id
        self.name = name
        self.business_id = business_id
        self.role = role
        self.active = active
    
    @staticmethod
    def generate_id():
        """Genera un ID de vendedor de 8 caracteres alfanuméricos"""
        characters = string.ascii_uppercase + string.digits
        return ''.join(random.choices(characters, k=8))
    
    @staticmethod
    def validate_id(vendor_id):
        """Valida que el ID tenga 8 caracteres alfanuméricos"""
        return vendor_id and len(vendor_id) == 8 and vendor_id.isalnum()
    
    def to_dict(self):
        """Convierte el modelo a diccionario"""
        return {
            'id': self.id,
            'name': self.name,
            'business_id': self.business_id,
            'role': self.role,
            'active': self.active
        }
    
    @classmethod
    def from_db_row(cls, row):
        """Crea una instancia desde una fila de base de datos"""
        if not row:
            return None
        return cls(
            id=row[0],
            name=row[1],
            business_id=row[2],
            role=row[3] if len(row) > 3 else 'vendedor',
            active=row[4] if len(row) > 4 else True
        )
    
    def __repr__(self):
        return f"<Vendor(id={self.id}, name={self.name}, business_id={self.business_id})>"
