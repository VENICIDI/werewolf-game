import struct, zlib, os, math

def create_png(filename, pixels, width, height):
    """Create a PNG from pixel data (list of (r,g,b,a) tuples)"""
    raw = b''
    for y in range(height):
        raw += b'\x00'  # filter byte
        for x in range(width):
            idx = y * width + x
            r, g, b, a = pixels[idx]
            raw += bytes([r, g, b, a])

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


def dist(x1, y1, x2, y2):
    return math.sqrt((x1 - x2) ** 2 + (y1 - y2) ** 2)


def make_home_icon(r, g, b, size=48):
    """Draw a house icon: triangle roof + rectangle body + small door"""
    pixels = [(0, 0, 0, 0)] * (size * size)
    cx = size / 2

    for y in range(size):
        for x in range(size):
            hit = False
            # Roof: triangle from (cx, 4) to (4, 22) to (size-4, 22)
            roof_top = 6
            roof_bottom = 24
            roof_left = 5
            roof_right = size - 5
            if roof_top <= y <= roof_bottom:
                progress = (y - roof_top) / (roof_bottom - roof_top)
                left = cx - progress * (cx - roof_left)
                right = cx + progress * (roof_right - cx)
                if left <= x <= right:
                    hit = True

            # Body: rectangle
            body_top = 22
            body_bottom = size - 6
            body_left = 10
            body_right = size - 10
            if body_top <= y <= body_bottom and body_left <= x <= body_right:
                hit = True

            # Door cutout (transparent) - small rectangle at bottom center
            door_top = 30
            door_bottom = size - 6
            door_left = int(cx - 4)
            door_right = int(cx + 4)
            if door_top <= y <= door_bottom and door_left <= x <= door_right:
                # Door fill (slightly different shade)
                hit = True
                # Make door a slightly darker shade
                if hit:
                    pixels[y * size + x] = (max(0, r - 40), max(0, g - 40), max(0, b - 40), 255)
                    continue

            if hit:
                pixels[y * size + x] = (r, g, b, 255)

    return pixels, size


def make_room_icon(r, g, b, size=48):
    """Draw a people/group icon: two overlapping person silhouettes"""
    pixels = [(0, 0, 0, 0)] * (size * size)

    def draw_person(px, head_cy, head_r, body_top, body_bottom, shoulder_w):
        for y in range(size):
            for x in range(size):
                # Head circle
                if dist(x, y, px, head_cy) <= head_r:
                    pixels[y * size + x] = (r, g, b, 255)
                # Body (half ellipse / arc shape)
                if body_top <= y <= body_bottom:
                    progress = (y - body_top) / max(1, (body_bottom - body_top))
                    w = shoulder_w * math.sin(progress * math.pi * 0.5 + 0.3)
                    if abs(x - px) <= w:
                        pixels[y * size + x] = (r, g, b, 255)

    # Back person (slightly left and behind)
    draw_person(px=18, head_cy=14, head_r=5.5, body_top=22, body_bottom=40, shoulder_w=10)
    # Front person (slightly right and in front)
    draw_person(px=30, head_cy=12, head_r=6, body_top=20, body_bottom=42, shoulder_w=11)

    return pixels, size


def make_profile_icon(r, g, b, size=48):
    """Draw a single person icon: head circle + body arc"""
    pixels = [(0, 0, 0, 0)] * (size * size)
    cx = size / 2

    for y in range(size):
        for x in range(size):
            # Head
            if dist(x, y, cx, 14) <= 8:
                pixels[y * size + x] = (r, g, b, 255)

            # Body / shoulders arc
            body_top = 26
            body_bottom = 44
            if body_top <= y <= body_bottom:
                progress = (y - body_top) / (body_bottom - body_top)
                w = 16 * math.sin(progress * math.pi * 0.45 + 0.35)
                if abs(x - cx) <= w:
                    pixels[y * size + x] = (r, g, b, 255)

    return pixels, size


def apply_antialiasing(pixels, size):
    """Simple box blur for anti-aliasing on alpha channel edges"""
    result = list(pixels)
    for y in range(1, size - 1):
        for x in range(1, size - 1):
            idx = y * size + x
            r0, g0, b0, a0 = pixels[idx]
            if a0 == 0:
                # Check if near an opaque pixel
                neighbors = [
                    pixels[(y - 1) * size + x],
                    pixels[(y + 1) * size + x],
                    pixels[y * size + (x - 1)],
                    pixels[y * size + (x + 1)],
                ]
                opaque_count = sum(1 for nr, ng, nb, na in neighbors if na > 0)
                if opaque_count >= 2:
                    # Blend: semi-transparent edge
                    avg_r = sum(nr for nr, ng, nb, na in neighbors if na > 0) // max(1, opaque_count)
                    avg_g = sum(ng for nr, ng, nb, na in neighbors if na > 0) // max(1, opaque_count)
                    avg_b = sum(nb for nr, ng, nb, na in neighbors if na > 0) // max(1, opaque_count)
                    result[idx] = (avg_r, avg_g, avg_b, int(255 * opaque_count / 4 * 0.5))
    return result


os.makedirs('images', exist_ok=True)

# Normal state: gray #999999
# Active state: red #e94560
icons = {
    'home': make_home_icon,
    'room': make_room_icon,
    'profile': make_profile_icon,
}

for name, func in icons.items():
    # Normal (gray)
    pixels, size = func(153, 153, 153)
    pixels = apply_antialiasing(pixels, size)
    create_png(f'images/{name}.png', pixels, size, size)

    # Active (red)
    pixels, size = func(233, 69, 96)
    pixels = apply_antialiasing(pixels, size)
    create_png(f'images/{name}-active.png', pixels, size, size)

print('Created 6 icon files (48x48 with shapes)')
