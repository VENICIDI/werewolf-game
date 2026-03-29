import struct, zlib, os

def create_png(filename, r, g, b):
    """Create a minimal 24x24 solid color PNG"""
    width, height = 24, 24
    raw = b''
    for y in range(height):
        raw += b'\x00'
        for x in range(width):
            raw += bytes([r, g, b, 255])

    def chunk(ct, data):
        c = ct + data
        crc = zlib.crc32(c) & 0xffffffff
        return struct.pack('>I', len(data)) + c + struct.pack('>I', crc)

    ihdr = struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0)
    with open(filename, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n')
        f.write(chunk(b'IHDR', ihdr))
        f.write(chunk(b'IDAT', zlib.compress(raw)))
        f.write(chunk(b'IEND', b''))

os.makedirs('images', exist_ok=True)
for name in ['home', 'room', 'profile']:
    create_png(f'images/{name}.png', 153, 153, 153)
    create_png(f'images/{name}-active.png', 233, 69, 96)
print('Created 6 icon files')
