"""Verify DEX replacement and preservation of native-library offsets."""
import pathlib
import struct
import subprocess
import tempfile
import unittest
import zipfile

TOOL = pathlib.Path(__file__).with_name('DexOverlay.java')


def dex(marker):
    return b'dex\n035\0' + bytes([marker]) * 104


def data_offset(path, entry):
    with path.open('rb') as stream:
        stream.seek(entry.header_offset + 26)
        name_size, extra_size = struct.unpack('<HH', stream.read(4))
    return entry.header_offset + 30 + name_size + extra_size


class DexOverlayTest(unittest.TestCase):
    def test_replaces_multidex_without_moving_native_libraries(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            stock, patch, output = (root / name for name in ('stock.apk', 'patch.apk', 'out.apk'))
            with zipfile.ZipFile(stock, 'w') as archive:
                archive.writestr('classes.dex', dex(1))
                archive.writestr('classes10.dex', dex(2))
                archive.writestr('lib/arm64-v8a/libwebview.so', b'native library fixture')
            original = stock.read_bytes()
            with zipfile.ZipFile(patch, 'w') as archive:
                archive.writestr('classes.dex', dex(3))
                archive.writestr('classes10.dex', dex(4))
                archive.writestr('classes11.dex', dex(5))
            subprocess.run(['java', str(TOOL), str(stock), str(patch), str(output)], check=True)
            self.assertEqual(stock.read_bytes(), original)
            self.assertTrue(output.read_bytes().startswith(original))
            with zipfile.ZipFile(stock) as before, zipfile.ZipFile(output) as after:
                self.assertEqual(after.read('classes.dex'), dex(3))
                self.assertEqual(after.read('classes10.dex'), dex(4))
                self.assertEqual(after.read('classes11.dex'), dex(5))
                name = 'lib/arm64-v8a/libwebview.so'
                self.assertEqual(after.read(name), before.read(name))
                self.assertEqual(data_offset(stock, before.getinfo(name)), data_offset(output, after.getinfo(name)))
                for name in ('classes.dex', 'classes10.dex', 'classes11.dex'):
                    self.assertEqual(data_offset(output, after.getinfo(name)) % 4, 0)
            result = subprocess.run(['java', str(TOOL), str(stock), str(patch), str(output)], capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(b'Output already exists', result.stderr)


if __name__ == '__main__':
    unittest.main()
