#!/usr/bin/env python3
from PIL import Image, ImageDraw, ImageFont
import os

# Crear directoris per a les icones
base_path = "/home/claude/TrobaCar/app/src/main/res"
densities = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192
}

for density, size in densities.items():
    density_path = os.path.join(base_path, density)
    os.makedirs(density_path, exist_ok=True)
    
    # Crear icona
    img = Image.new('RGB', (size, size), color='#1976D2')
    draw = ImageDraw.Draw(img)
    
    # Dibuixar cercle blanc
    margin = size // 8
    draw.ellipse([margin, margin, size-margin, size-margin], fill='white')
    
    # Dibuixar forma de cotxe simplificada (rectangle amb rodes)
    car_color = '#1976D2'
    car_width = size // 2
    car_height = size // 4
    car_x = (size - car_width) // 2
    car_y = size // 2
    
    # Cos del cotxe
    draw.rectangle([car_x, car_y, car_x + car_width, car_y + car_height], fill=car_color)
    
    # Finestres (rectangles més petits)
    window_margin = car_width // 6
    draw.rectangle([car_x + window_margin, car_y - car_height//3, 
                   car_x + car_width - window_margin, car_y], fill=car_color)
    
    # Rodes
    wheel_radius = car_height // 3
    wheel_y = car_y + car_height - wheel_radius // 2
    # Roda esquerra
    draw.ellipse([car_x + wheel_radius, wheel_y - wheel_radius,
                 car_x + wheel_radius * 3, wheel_y + wheel_radius], fill='#333333')
    # Roda dreta
    draw.ellipse([car_x + car_width - wheel_radius * 3, wheel_y - wheel_radius,
                 car_x + car_width - wheel_radius, wheel_y + wheel_radius], fill='#333333')
    
    # Pin de localització vermell
    pin_size = size // 6
    pin_x = size // 2
    pin_y = size // 3
    
    # Part rodona del pin
    draw.ellipse([pin_x - pin_size//2, pin_y - pin_size//2,
                 pin_x + pin_size//2, pin_y + pin_size//2], fill='#F44336')
    
    # Punta del pin (triangle)
    draw.polygon([(pin_x, pin_y + pin_size//2),
                 (pin_x - pin_size//3, pin_y),
                 (pin_x + pin_size//3, pin_y)], fill='#F44336')
    
    # Punt blanc dins el pin
    dot_size = pin_size // 4
    draw.ellipse([pin_x - dot_size//2, pin_y - dot_size//2,
                 pin_x + dot_size//2, pin_y + dot_size//2], fill='white')
    
    # Guardar icona normal
    img.save(os.path.join(density_path, 'ic_launcher.png'))
    
    # Crear versió rodona (amb màscara circular)
    img_round = Image.new('RGB', (size, size), color='#1976D2')
    draw_round = ImageDraw.Draw(img_round)
    
    # Dibuixar cercle blanc
    draw_round.ellipse([margin, margin, size-margin, size-margin], fill='white')
    
    # Copiar el cotxe i pin
    draw_round.rectangle([car_x, car_y, car_x + car_width, car_y + car_height], fill=car_color)
    draw_round.rectangle([car_x + window_margin, car_y - car_height//3, 
                         car_x + car_width - window_margin, car_y], fill=car_color)
    draw_round.ellipse([car_x + wheel_radius, wheel_y - wheel_radius,
                       car_x + wheel_radius * 3, wheel_y + wheel_radius], fill='#333333')
    draw_round.ellipse([car_x + car_width - wheel_radius * 3, wheel_y - wheel_radius,
                       car_x + car_width - wheel_radius, wheel_y + wheel_radius], fill='#333333')
    draw_round.ellipse([pin_x - pin_size//2, pin_y - pin_size//2,
                       pin_x + pin_size//2, pin_y + pin_size//2], fill='#F44336')
    draw_round.polygon([(pin_x, pin_y + pin_size//2),
                       (pin_x - pin_size//3, pin_y),
                       (pin_x + pin_size//3, pin_y)], fill='#F44336')
    draw_round.ellipse([pin_x - dot_size//2, pin_y - dot_size//2,
                       pin_x + dot_size//2, pin_y + dot_size//2], fill='white')
    
    img_round.save(os.path.join(density_path, 'ic_launcher_round.png'))
    
    print(f"Created icons for {density}")

print("All icons created successfully!")
