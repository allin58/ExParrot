#!/usr/bin/python

from datetime import datetime, timedelta
import time
import RPi.GPIO as GPIO
import sys
import os
import time
import json
import qrcode
from collections import deque
import imutils
import logging
from gpiozero import Button
from gpiozero import LED
from time import sleep
from hdwallet.cryptocurrencies import BitcoinTestnet
from mnemonic import Mnemonic
from hdwallet import BIP84HDWallet
from hdwallet.cryptocurrencies import BitcoinMainnet
from PIL import Image, ImageDraw, ImageFont
from imutils.video import VideoStream
from pyzbar import pyzbar
from typing import  Optional, Union
picdir = os.path.join(os.path.dirname(os.path.realpath(__file__)), 'pic')
libdir = os.path.join(os.path.dirname(os.path.realpath(__file__)), 'lib')
if os.path.exists(libdir):
    sys.path.append(libdir)
from waveshare_epd import epd4in2

class Base43Decoder():

    def __init__(self):
        self.__b43chars = b'0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ$*+-./:'
        # assert len(__b43chars) == 43
        self.__b43chars_inv = self.inv_dict(dict(enumerate(self.__b43chars)))
        self.__b58chars = b'123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz'
        # assert len(__b58chars) == 58
        self.__b58chars_inv = self.inv_dict(dict(enumerate(self.__b58chars)))
        logging.info("Base43Decoder is created")

    def assert_bytes(self, *args):
        """
        porting helper, assert args type
        """
        try:
            for x in args:
                assert isinstance(x, (bytes, bytearray))
        except:
            print('assert bytes failed', list(map(type, args)))
            raise

    def to_bytes(self, something, encoding='utf8') -> bytes:
        """
        cast string to bytes() like object, but for python2 support it's bytearray copy
        """
        if isinstance(something, bytes):
            return something
        if isinstance(something, str):
            return something.encode(encoding)
        elif isinstance(something, bytearray):
            return bytes(something)
        else:
            raise TypeError("Not a string or bytes like object")

    def inv_dict(self, d):
        return {v: k for k, v in d.items()}


    def base_encode(self, v: bytes, base: int) -> str:
        """ encode v, which is a string of bytes, to base58."""
        self.assert_bytes(v)
        if base not in (58, 43):
            raise ValueError('not supported base: {}'.format(base))
        chars = self.__b58chars
        if base == 43:
            chars = self.__b43chars

        origlen = len(v)
        v = v.lstrip(b'\x00')
        newlen = len(v)

        num = int.from_bytes(v, byteorder='big')
        string = b""
        while num:
            num, idx = divmod(num, base)
            string = chars[idx:idx + 1] + string

        result = chars[0:1] * (origlen - newlen) + string
        return result.decode('ascii')

    def base_decode(self, v: Union[bytes, str], base: int) -> Optional[bytes]:
        """ decode v into a string of len bytes.
        based on the work of David Keijser in https://github.com/keis/base58
        """
        # assert_bytes(v)
        v = self.to_bytes(v, 'ascii')
        if base not in (58, 43):
            raise ValueError('not supported base: {}'.format(base))
        chars = self.__b58chars
        chars_inv = self.__b58chars_inv
        if base == 43:
            chars = self.__b43chars
            chars_inv = self.__b43chars_inv

        origlen = len(v)
        v = v.lstrip(chars[0:1])
        newlen = len(v)

        num = 0
        try:
            for char in v:
                num = num * base + chars_inv[char]
        except KeyError:
            print('Forbidden character {} for base {}'.format(char, base))

        return num.to_bytes(origlen - newlen + (num.bit_length() + 7) // 8, 'big')

class ExWallet():

    def __init__(self, led):
        self.reset = Button(23)
        self.in_pin = Button(22)
        self.clk = LED(27)
        self.entropy = ""
        self.net_type = "Testnet"
        self.net = BitcoinTestnet
        self.switch_reset()
        self.get_entropy()
        self.entropy_to_mnemonic()
        self.base_43_decoder = Base43Decoder()
        self.led = led
        electrum_deamon = os.popen("/home/pi/.local/bin/electrum --version").read()
        logging.info(electrum_deamon)
        logging.info("ExWallet is created")

    def switch_net_type(self, net_type):
        if net_type == "Mainnet":
            self.net_type = net_type
            self.net = BitcoinMainnet
        if net_type == "Testnet":
            self.net_type = net_type
            self.net = BitcoinTestnet

    def pulse(self):
        self.clk.on()
        sleep(0.001)
        self.clk.off()
        sleep(0.001)

    def switch_reset(self):
        i = 0
        while True:
            i = i + 1
            if not self.reset.is_pressed:
                break
            self.pulse()

    def get_entropy(self):
        self.entropy = ""
        for i in range(1, 129, 1):
            self.pulse()
            if self.in_pin.is_pressed:
                self.entropy = self.entropy + "0"
            else:
                self.entropy = self.entropy + "1"

    def bitstring_to_bytes(self, s):
        return int(s, 2).to_bytes((len(s) + 7) // 8, byteorder='big')

    def entropy_to_mnemonic(self):
        mnemo = Mnemonic("english")
        self.mnemonic = mnemo.to_mnemonic(self.bitstring_to_bytes(self.entropy))

    def master_public_key(self):
        wallet = BIP84HDWallet(cryptocurrency=self.net)
        wallet.from_mnemonic(mnemonic=self.mnemonic)
        wallet.clean_derivation()
        wallet.clean_derivation()
        wallet.from_path("m/84'/1'/0'")
        return wallet.xpublic_key()

    def private_key(self):
        wallet = BIP84HDWallet(cryptocurrency=self.net)
        wallet.from_mnemonic(mnemonic=self.mnemonic)
        wallet.clean_derivation()
        wallet.clean_derivation()
        wallet.from_path("m/84'/1'/0'")
        return wallet.xprivate_key()

    def generate_addresses(self):
        wallet = BIP84HDWallet(cryptocurrency=self.net)
        wallet.from_mnemonic(mnemonic=self.mnemonic)
        wallet.clean_derivation()
        adresses = []
        for i in range(15):
            wallet.from_path("m/84'/1'/0'/0/" + str(i))
            adresses.append(wallet.address())
            wallet.clean_derivation()
        return adresses

    def get_bar(self):
        logging.info("ExWallet get_QR")
        vs = VideoStream(usePiCamera=True).start()
        time.sleep(2.0)
        while True:
            frame = vs.read()
            frame = imutils.resize(frame, width=400)
            barcodes = pyzbar.decode(frame)
            if len(barcodes) > 0:
                self.led.off()
                time.sleep(3.0)
                led.blink(0.2, 0.3)
                logging.info("ExWallet bar_is_found")
                barcodeData = barcodes[0].data.decode("utf-8")
                vs.stop()
                return barcodeData

class Scan():

    def __init__(self, exwallet):
        self.is_render = True
        self.is_active = True
        self.exwallet = exwallet
        self.scene = 0
        self.qr_psbt = ""
        logging.info("Scan is created")

    def to_signed_tx_arr(self, signed_tx, step):
        logging.info("Scan to_signed_tx_arr")
        signed_tx_arr = []
        while len(signed_tx) != 0:
            signed_tx_arr.append(signed_tx[:step])
            signed_tx = signed_tx[step:]
        matrix_width  = 75
        for signeditem in  signed_tx_arr:
            qr = qrcode.QRCode(version=1, border=0)
            qr.add_data(signeditem)
            qr.make(fit=True)
            qr_matrix = qr.get_matrix()
            matrix_width_interim = len(qr_matrix)
            if matrix_width_interim > matrix_width:
                matrix_width = matrix_width_interim

        if(matrix_width > 75):
            return self.to_signed_tx_arr(self.signed_tx, step - 10)
        return signed_tx_arr

    def action_handler(self, action):
        logging.info("Scan action_handler")
        if self.scene == 0 and action == "both_button":
           self.qr_psbt_base_43 = self.exwallet.get_bar()
           self.qr_psbt_bytes = self.exwallet.base_43_decoder.to_bytes(self.qr_psbt_base_43)
           self.qr_psbt_hex = self.exwallet.base_43_decoder.base_decode(self.qr_psbt_bytes, 43).hex()
           self.deserialize_psbt = os.popen("/home/pi/.local/bin/electrum deserialize --offline --" + self.exwallet.net_type.lower() + " " + self.qr_psbt_hex).read()
           self.json_psbt = json.loads(self.deserialize_psbt)
           self.scene = 1
           return "scan"
        if self.scene == 1 and action == "both_button":
           self.scene = 2
           pk = self.exwallet.private_key()
           if "wallet" in os.popen("ls").read():
               os.system("rm wallet")
           os.system("echo \"" + pk + "\"" + " | /home/pi/.local/bin/electrum --" + self.exwallet.net_type.lower() + " --offline -w wallet restore -")
           self.signed_tx = os.popen("/home/pi/.local/bin/electrum signtransaction --" + self.exwallet.net_type.lower() + " --offline --forgetconfig --wallet wallet " + self.qr_psbt_hex).read().rstrip()
           if "wallet" in os.popen("ls").read():
               os.system("rm wallet")
           step = 380
           self.signed_tx_arr = self.to_signed_tx_arr( self.signed_tx, step)
           self.tx_index = 0
           return "scan"
        if self.scene == 1 and (action == "right_button" or action == "left_button"):
           self.scene = 0
           self.is_active = False
           return "mbar"
        if self.scene == 2 and action == "both_button":
           self.scene = 0
           self.is_active = False
           return "mbar"
        if self.scene == 2 and action == "right_button":
           if  self.tx_index + 1 < len(self.signed_tx_arr):
               self.tx_index = self.tx_index + 1
           return "scan"
        if self.scene == 2 and action == "left_button":
           if self.tx_index > 0 :
              self.tx_index = self.tx_index - 1
           return "scan"
        return "mbar"

    def render(self, draw):
        logging.info("Scan render")
        font12 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 12)
        font24 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 24)
        font18 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 18)
        font35 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 35)

        if self.scene == 0:
            draw.rectangle((95, 150, 200, 180), fill=255)
            draw.text((85, 150), 'QR Scanning ...', font=font24, fill=0)

        if self.scene == 1:
            inputs = self.json_psbt["inputs"]
            outputs = self.json_psbt["outputs"]
            input_offset = 35
            output_offset = 0
            fee_offset = 0
            draw.text((15, 15), 'TX inputs', font=font18, fill=0)
            tx_input_sum = 0
            tx_output_sum = 0
            for i in range(len(inputs)):
                tx_input_sum = tx_input_sum + inputs[i]["value_sats"]
                output_offset = input_offset + 30 + 32 * i
                draw.rectangle((5, input_offset + 32 * i, 295, input_offset + 30 + 32 * i), fill=0)
                draw.text((10, input_offset + 32 * i), inputs[i]["address"], font=font12, fill=255)
                sats = '{:0.8f}'.format(1 / 100000000 * inputs[i]["value_sats"])
                draw.text((10, input_offset + 15 + 32 * i), sats + " BTC", font=font12, fill=255)

            draw.text((15, output_offset + 15 ), 'TX outputs', font=font18, fill=0)
            output_offset = output_offset + 35
            for i in range(len(outputs)):
                tx_output_sum = tx_output_sum + outputs[i]["value_sats"]
                fee_offset = output_offset + 30 + 32 * i
                draw.rectangle((5, output_offset + 32 * i, 295, output_offset + 30 + 32 * i), fill=0)
                draw.text((10, output_offset + 32 * i), outputs[i]["address"], font=font12, fill=255)
                sats = '{:0.8f}'.format(1 / 100000000 * outputs[i]["value_sats"])
                draw.text((10, output_offset + 15 + 32 * i), sats + " BTC", font=font12, fill=255)

            sats = '{:0.8f}'.format(1 / 100000000 * (tx_input_sum - tx_output_sum))
            draw.text((15, fee_offset + 15), 'Fee', font=font18, fill=0)
            draw.rectangle((5, fee_offset + 35, 295, fee_offset + 50 ), fill=0)
            draw.text((10, fee_offset + 35), sats + " BTC", font=font12, fill=255)

        if self.scene == 2:
            str(self.tx_index + 1) + "/" + str(len(self.signed_tx_arr))
            draw.text((25, 25), str(self.tx_index + 1) + "/" + str(len(self.signed_tx_arr)), font=font24, fill=0)
            qr = qrcode.QRCode(version=1, border=0)
            qr.add_data(self.signed_tx_arr[self.tx_index])
            qr.make(fit=True)
            qr_matrix = qr.get_matrix()
            matrix_width = len(qr_matrix)
            x_offset = 0
            y_offset = 50
            for i in range(matrix_width):
                for j in range(matrix_width):
                    if qr_matrix[i][j]:
                       draw.rectangle((x_offset + i * 4, y_offset + j * 4, x_offset + i * 4 + 4, y_offset + j * 4 + 4), fill=0)

class Addr():

    def __init__(self, exwallet):
        self.is_render = False
        self.is_active = False
        self.exwallet = exwallet
        self.highlight_index = 0
        logging.info("Addr is created")

    def action_handler(self, action):
        logging.info("Addr action_handler")
        if action == "both_button":
            if self.is_active == False:
                self.is_active = True
                return "addr"
            if self.is_active == True:
                self.is_active = False
                return "mbar"
        if action == "right_button" and self.highlight_index < 13:
            self.highlight_index = self.highlight_index + 1
            return "addr"
        if action == "right_button" and self.highlight_index > 0:
            self.highlight_index = self.highlight_index - 1
            return "addr"
        return "mbar"

    def render(self, draw):
        logging.info("Addr render")
        font12 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 12)
        font24 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 24)
        font18 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 18)
        font35 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 35)
        addresses = exwallet.generate_addresses()
        qr = qrcode.QRCode(version=1, border=0)
        qr.add_data(addresses[self.highlight_index])
        qr.make(fit=True)
        qr_matrix = qr.get_matrix()
        matrix_width = len(qr_matrix)
        x_offset = 0
        y_offset = 0
        for i in range(matrix_width):
            for j in range(matrix_width):
                if qr_matrix[i][j]:
                    draw.rectangle((x_offset + i * 4, y_offset + j * 4, x_offset + i * 4 + 4, y_offset + j * 4 + 4), fill=0)

        for i in range(len(addresses)):
            if i == self.highlight_index:
                draw.rectangle((0, 125 + 15 * i, 300, 140 + 15 * i), fill=0)
                draw.text((10, 125 + 15 * i), addresses[i], font=font12, fill=255)
            else:
                draw.text((10, 125 + 15 * i), addresses[i], font=font12, fill=0)

class Key():

    def __init__(self, exwallet):
        self.is_render = False
        self.is_active = False
        self.exwallet = exwallet
        logging.info("Key is created")

    def action_handler(self, action):
        logging.info("Key action_handler")
        return "mbar"

    def render(self, draw):
        logging.info("Key render")
        font12 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 12)
        font24 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 24)
        font18 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 18)
        font35 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 35)
        self.exwallet.master_public_key()
        draw.text((0, 30), "Public master key (" + self.exwallet.net_type + ")", font=font24, fill=0)
        qr = qrcode.QRCode(version=1, border=0)
        qr.add_data(self.exwallet.master_public_key())
        qr.make(fit=True)
        qr_matrix = qr.get_matrix()
        matrix_width = len(qr_matrix)
        x_offset = 50
        y_offset = 70
        for i in range(matrix_width):
            for j in range(matrix_width):
                if qr_matrix[i][j]:
                    draw.rectangle((x_offset + i * 4, y_offset + j * 4, x_offset + i * 4 + 4, y_offset + j * 4 + 4), fill=0)

class Ent():

    def __init__(self, exwallet):
        self.is_render = False
        self.is_active = False
        self.exwallet = exwallet
        logging.info("Ent is created")

    def action_handler(self, action):
        logging.info("Ent render")
        return "mbar"

    def render(self, draw):
        logging.info("Ent render")
        font12 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 12)
        font24 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 24)
        font18 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 18)
        font35 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 35)
        y_offset = 45

        draw.text((25, 20), "Entrtopy", font=font24, fill=0)

        draw.rectangle((10, 12 + y_offset, 71, 32 + y_offset), outline=0)
        draw.text((13, 15 + y_offset), self.exwallet.entropy[0:8], font=font12, fill=0)
        draw.rectangle((83, 12 + y_offset, 144, 32 + y_offset), outline=0)
        draw.text((86, 15 + y_offset), self.exwallet.entropy[8:16], font=font12, fill=0)
        draw.rectangle((156, 12 + y_offset, 217, 32 + y_offset), outline=0)
        draw.text((159, 15 + y_offset), self.exwallet.entropy[16:24], font=font12, fill=0)
        draw.rectangle((229, 12 + y_offset, 290, 32 + y_offset), outline=0)
        draw.text((232, 15 + y_offset), self.exwallet.entropy[24:32], font=font12, fill=0)

        draw.rectangle((10, 44 + y_offset, 72, 64 + y_offset), outline=0)
        draw.text((13, 47 + y_offset), self.exwallet.entropy[32:40], font=font12, fill=0)
        draw.rectangle((83, 44 + y_offset, 144, 64 + y_offset), outline=0)
        draw.text((86, 47 + y_offset), self.exwallet.entropy[40:48], font=font12, fill=0)
        draw.rectangle((156, 44 + y_offset, 217, 64 + y_offset), outline=0)
        draw.text((159, 47 + y_offset), self.exwallet.entropy[48:56], font=font12, fill=0)
        draw.rectangle((229, 44 + y_offset, 290, 64 + y_offset), outline=0)
        draw.text((232, 47 + y_offset), self.exwallet.entropy[56:64], font=font12, fill=0)

        draw.rectangle((10, 76 + y_offset, 72, 96 + y_offset), outline=0)
        draw.text((13, 79 + y_offset), self.exwallet.entropy[64:72], font=font12, fill=0)
        draw.rectangle((83, 76 + y_offset, 144, 96 + y_offset), outline=0)
        draw.text((86, 79 + y_offset), self.exwallet.entropy[72:80], font=font12, fill=0)
        draw.rectangle((156, 76 + y_offset, 217, 96 + y_offset), outline=0)
        draw.text((159, 79 + y_offset), self.exwallet.entropy[80:88], font=font12, fill=0)
        draw.rectangle((229, 76 + y_offset, 290, 96 + y_offset), outline=0)
        draw.text((232, 79 + y_offset), self.exwallet.entropy[88:96], font=font12, fill=0)

        draw.rectangle((10, 108 + y_offset, 72, 128 + y_offset), outline=0)
        draw.text((13, 111 + y_offset), self.exwallet.entropy[96:104], font=font12, fill=0)
        draw.rectangle((83, 108 + y_offset, 144, 128 + y_offset), outline=0)
        draw.text((86, 111 + y_offset), self.exwallet.entropy[104:112], font=font12, fill=0)
        draw.rectangle((156, 108 + y_offset, 217, 128 + y_offset), outline=0)
        draw.text((159, 111 + y_offset), self.exwallet.entropy[112:120], font=font12, fill=0)
        draw.rectangle((229, 108 + y_offset, 290, 128 + y_offset), outline=0)
        draw.text((232, 111 + y_offset), self.exwallet.entropy[120:128], font=font12, fill=0)

        draw.text((25, 200), "Mnemonic", font=font24, fill=0)
        mnemonic_arr = self.exwallet.mnemonic.split(" ")

        line_number = 0
        sub_mnemonic = ""
        for i in range(len(mnemonic_arr)):
            if i + 1 == len(mnemonic_arr):
                next_word_len = 0
            else:
                next_word_len = len(mnemonic_arr[i + 1])
            if len(sub_mnemonic) + next_word_len < 30:
                sub_mnemonic = sub_mnemonic + mnemonic_arr[i] + " "
            else:
                draw.text((15, 230 + 20 * line_number), sub_mnemonic, font=font18, fill=0)
                sub_mnemonic = mnemonic_arr[i] + " "
                line_number = line_number + 1
        draw.text((15, 230 + 20 * line_number), sub_mnemonic, font=font18, fill=0)

class Net():

    def __init__(self, exwallet):
        self.is_render = False
        self.is_active = False
        self.exwallet = exwallet
        logging.info("Net is created")

    def action_handler(self, action):
        logging.info("Net action_handler")
        if self.exwallet.net_type == "Mainnet":
            self.exwallet.switch_net_type("Testnet")
            return "mbar"
        if self.exwallet.net_type == "Testnet":
            self.exwallet.switch_net_type("Mainnet")
            return "mbar"

    def render(self, draw):
        logging.info("Net render")
        font12 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 12)
        font24 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 24)
        font18 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 18)
        font35 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 35)

        if exwallet.net_type == "Mainnet":
            draw.rectangle((95, 150, 200, 180), fill=0)
            draw.rectangle((95, 182, 200, 212), fill=255)
            draw.text((105, 150), 'Mainnet', font=font24, fill=255)
            draw.text((105, 182), 'Testnet', font=font24, fill=0)

        if exwallet.net_type == "Testnet":
            draw.rectangle((95, 150, 200, 180), fill=255)
            draw.rectangle((95, 182, 200, 212), fill=0)
            draw.text((105, 150), 'Mainnet', font=font24, fill=0)
            draw.text((105, 182), 'Testnet', font=font24, fill=255)

        draw.rectangle((95, 150, 200, 180), outline=0)
        draw.rectangle((95, 182, 200, 212), outline=0)

class Exit():

    def __init__(self):
        self.is_render = False
        self.is_active = False
        logging.info("Exit is created")

    def action_handler(self, action):
        logging.info("Exit action_handler")
        os.system("systemctl poweroff")
        return "mbar"

    def render(self, draw):
        logging.info("Exit render")
        font12 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 12)
        font24 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 24)
        font18 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 18)
        font35 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 35)
        draw.text((105, 150), 'poweroff', font=font24, fill=0)

class MBar():

    def __init__(self, render_map_for_MBar):
        self.current_index = 0
        self.is_render = True
        self.is_active = False
        self.render_map_for_MBar = render_map_for_MBar
        logging.info("MBar is created")

    def get_mbar_cell(self):
        menu = ["scan", "addr", "key", "ent", "net", "exit"]
        return menu[self.current_index]

    def action_handler(self, action):
        logging.info("MBar action_handler")
        self.current_index = self.current_index + 12

        if action == "right_button":
            self.current_index = self.current_index + 1

        if action == "left_button":
            self.current_index = self.current_index - 1

        self.current_index = self.current_index % 6

        if action == "both_button":
            return render_map_for_MBar[self.get_mbar_cell()].action_handler("both_button")
            # if self.get_mbar_cell() == "net":
            #     render_map_for_MBar["net"].action_handler("both_button")
            # if self.get_mbar_cell() == "exit":
            #     render_map_for_MBar["net"].action_handler("both_button")

        return "mbar"

    def render(self, draw):
        logging.info("MBar render")
        scan = [255, 0]
        addr = [255, 0]
        key = [255, 0]
        ent = [255, 0]
        net = [255, 0]
        exit = [255, 0]
        menu = ["scan", "addr", "key", "ent", "net", "exit"]
        self.mm = {"scan": scan, "addr": addr, "key": key, "ent": ent, "net": net, "exit": exit, }
        self.mm[menu[self.current_index]] = [0, 255]
        font12 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 12)
        font24 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 24)
        font18 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 18)
        font35 = ImageFont.truetype(os.path.join(picdir, 'Font.ttc'), 35)
        # scan
        draw.rectangle((1, 370, 60, 399), fill=self.mm["scan"][0])
        draw.rectangle((1, 370, 60, 399), outline=0)
        draw.text((7, 370), 'scan', font=font24, fill=self.mm["scan"][1])

        # addr
        draw.rectangle((60, 370, 120, 399), fill=self.mm["addr"][0])
        draw.rectangle((60, 370, 120, 399), outline=0)
        draw.text((65, 370), 'addr', font=font24, fill=self.mm["addr"][1])

        # key
        draw.rectangle((120, 370, 165, 399), fill=self.mm["key"][0])
        draw.rectangle((120, 370, 165, 399), outline=0)
        draw.text((125, 370), 'key', font=font24, fill=self.mm["key"][1])

        # ent
        draw.rectangle((165, 370, 210, 399), fill=self.mm["ent"][0])
        draw.rectangle((165, 370, 210, 399), outline=0)
        draw.text((170, 370), 'ent', font=font24, fill=self.mm["ent"][1])

        # net
        draw.rectangle((210, 370, 255, 399), fill=self.mm["net"][0])
        draw.rectangle((210, 370, 255, 399), outline=0)
        draw.text((215, 370), 'net', font=font24, fill=self.mm["net"][1])

        # exit
        draw.rectangle((255, 370, 299, 399), fill=self.mm["exit"][0])
        draw.rectangle((255, 370, 299, 399), outline=0)
        draw.text((258, 370), 'exit', font=font24, fill=self.mm["exit"][1])

class Eink():

    def __init__(self):
        logging.info("Eink is created")

    def draw(self, render_arr):
        logging.info("Eink draw")
        try:
            epd = epd4in2.EPD()
            epd.init()
            epd.Clear()
            Himage = Image.new('1', (epd.height, epd.width), 255)  # 255: clear the frame
            draw = ImageDraw.Draw(Himage)
            for r in render_arr:
                if r.is_render:
                    r.render(draw)
            epd.display(epd.getbuffer(Himage))
            # time.sleep(2)
            epd.sleep()
        except IOError as e:
            logging.info("error")

        except KeyboardInterrupt:
            epd4in2.epdconfig.module_exit()
            exit()

class Tanos():

    def __init__(self, render_map):
        self.render_map = render_map
        self.active_frame = "mbar"
        logging.info("Tanos is created")

    def route_action(self, action):
        logging.info("Tanos route_action")
        self.active_frame = self.render_map[self.active_frame].action_handler(action)
        temp_render_map = self.render_map.copy()
        temp_render_map.pop("mbar")
        for frame in temp_render_map.values():
            frame.is_render = False
        render_map[render_map["mbar"].get_mbar_cell()].is_render = True


logging.basicConfig(filename='/home/pi/exparrot-script/exparrot.log', encoding='utf-8', level=logging.INFO, format='%(asctime)s %(message)s')
logging.info("-----------------------------------------")
led = LED(13)
exwallet = ExWallet(led)
eink = Eink()
scan = Scan(exwallet)
addr = Addr(exwallet)
key = Key(exwallet)
ent = Ent(exwallet)
net = Net(exwallet)
exit = Exit()
render_map_for_MBar = {"scan": scan, "addr": addr, "net": net, "exit": exit}
mbar = MBar(render_map_for_MBar)
render_map = {"mbar": mbar, "scan": scan, "addr": addr, "key": key, "ent": ent, "net": net, "exit": exit}
tanos = Tanos(render_map)
BUTTON_PIN_1 = 26
BUTTON_PIN_0 = 19
GPIO.setmode(GPIO.BCM)
GPIO.setup(BUTTON_PIN_0, GPIO.IN, pull_up_down=GPIO.PUD_UP)
GPIO.setup(BUTTON_PIN_1, GPIO.IN, pull_up_down=GPIO.PUD_UP)
PIN0_STATE = 0
PIN1_STATE = 0
eink.draw(render_map.values())
led.on()
button_arr = []
time_check_poin_queue = deque()
def button0_handler():
    led.blink(0.2, 0.3)
    logging.info("button 0 handler")
    tanos.route_action("right_button")
    eink.draw(render_map.values())
    led.on()

def button1_handler():
    led.blink(0.2, 0.3)
    logging.info("button 1 handler")
    tanos.route_action("left_button")
    eink.draw(render_map.values())
    led.on()

def both_button_handler():
    led.blink(0.2, 0.3)
    logging.info("both button handler")
    tanos.route_action("both_button")
    eink.draw(render_map.values())
    led.on()

def PIN_0_pressed(channel):
    global button_arr
    global time_check_poin_queue
    time_check_poin_queue.append(time.time())
    button_arr.append(10)
    logging.info("PIN_0_pressed button_arr=%d",sum(button_arr))

def PIN_1_pressed(channel):
    global button_arr
    global time_check_poin_queue
    time_check_poin_queue.append(time.time())
    button_arr.append(18)
    logging.info("PIN_1_pressed button_arr=%d", sum(button_arr))

GPIO.add_event_detect(BUTTON_PIN_1, GPIO.BOTH, callback=PIN_1_pressed, bouncetime=50)
GPIO.add_event_detect(BUTTON_PIN_0, GPIO.BOTH, callback=PIN_0_pressed, bouncetime=50)

try:
    while True:
        time.sleep(0.01)
        if sum(button_arr) > 0:
            if sum(button_arr) == 20:
                button0_handler()
                button_arr.clear()
            if sum(button_arr) == 36:
                button1_handler()
                button_arr.clear()
            if sum(button_arr) == 38 or sum(button_arr) == 46:
                time_check_poin_queue.pop()
                time_diff = time.time() - time_check_poin_queue.pop()
                logging.info("time diff %d", time_diff)
                if time_diff > 5:
                    logging.info("shutdown")
                    os.system("systemctl poweroff")
                    time.sleep(60)
                both_button_handler()
                button_arr.clear()
            if sum(button_arr) > 46 :
                button_arr.clear()
except Exception as e:
    logging.info('An exception occurred: {}'.format(e))