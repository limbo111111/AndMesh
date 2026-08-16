h0 = 0x1A
h1_low_nibble = 0x09
a = [(h0 >> 4) & 1, (h0 >> 5) & 1, (h0 >> 6) & 1, (h0 >> 7) & 1]
b = [(h0 >> 0) & 1, (h0 >> 1) & 1, (h0 >> 2) & 1, (h0 >> 3) & 1]
c = [
    (h1_low_nibble >> 0) & 1,
    (h1_low_nibble >> 1) & 1,
    (h1_low_nibble >> 2) & 1,
    (h1_low_nibble >> 3) & 1,
]

print(f"a: {a}")
print(f"b: {b}")
print(f"c: {c}")

bit4 = a[0] ^ a[1] ^ a[2] ^ a[3]
bit3 = a[3] ^ b[1] ^ b[2] ^ b[3] ^ c[0]
bit2 = a[2] ^ b[0] ^ b[3] ^ c[1] ^ c[3]
bit1 = a[1] ^ b[0] ^ b[2] ^ c[0] ^ c[1] ^ c[2]
bit0 = a[0] ^ b[1] ^ c[0] ^ c[1] ^ c[2] ^ c[3]

res = (bit4 << 4) | (bit3 << 3) | (bit2 << 2) | (bit1 << 1) | bit0
print(f"res: {res}")
