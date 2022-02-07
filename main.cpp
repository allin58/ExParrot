#include "mbed.h"
#include "USBHostMSD.h"
#include "Bitcoin.h"
#include "PSBT.h"
#include "Electrum.h"
#include "Conversion.h"
#include "bip39.h"
#include <string.h>
#include <BaseClasses.h>
#include <map>


DigitalOut led(PC_13);
DigitalOut clk(PB_8);
DigitalIn in(PB_9);
DigitalIn reset(PB_7);


void pulse() {
    clk=1;
    Thread::wait(2);
    clk=0;    
}

void take_ref() {
 while(reset==0){
 pulse();
 } 
}

std::string get_bit(){
      std::string temp_string="";
     if(in == 1){
        temp_string = "1";
      }else{
        temp_string = "0";
     }
    return temp_string;
    
}


std::string read_switch()  {
    std::string data_switch = "";
    for (int i = 0; i < 8; i++){
        pulse();
        data_switch=data_switch + get_bit();
        }
    return data_switch;
}

std::string read_switches()  {
    
    std::string data_switch = "";
    for (int i = 0; i < 16; i++){
        pulse();
        data_switch=data_switch + read_switch();
        }
    return data_switch;
}

std::string read_all()  {
    take_ref(); 
    std::string data_switch = "";
    for (int i = 0; i < 128; i++){
        pulse();
        data_switch=data_switch + get_bit();
        }
    return data_switch;
}


std::string read_half_of_byte()  {
    std::string data_switch = "";
    for (int i = 0; i < 4; i++){
        pulse();
        data_switch=data_switch + get_bit();
        }
    return data_switch;
}


uint8_t c2hex(const char c)
{
    uint8_t u = 0;

    if ('0' <= c && c <= '9')
        u = c - '0';
    else if ('a' <= c && c <= 'f')
        u = c - 'W';
    else if ('A' <= c && c <= 'F')
        u = c - '7';


    return u;
}

char bit4ToHex(std::string bits){
    std::map<std::string, char> mp;
    mp["0000"] = '0';
    mp["0001"] = '1';
    mp["0010"] = '2';
    mp["0011"] = '3';
    mp["0100"] = '4';
    mp["0101"] = '5';
    mp["0110"] = '6';
    mp["0111"] = '7';
    mp["1000"] = '8';
    mp["1001"] = '9';
    mp["1010"] = 'A';
    mp["1011"] = 'B';
    mp["1100"] = 'C';
    mp["1101"] = 'D';
    mp["1110"] = 'E';
    mp["1111"] = 'F';
    return mp[bits];
}

std::string uint8_tToString(uint8_t ua){
        int a = ua+0;
        char buf[10];        
        sprintf(buf, "%d", a);
        return  buf; 
}

std::string convertToString(char* a, int size)
{    
    int i;
    string s = "";
    for (i = 0; i < size; i++) {
        s = s + a[i];
    }
    return s;
}





int main() {
    
      USBHostMSD msd("usb");
      while(!msd.connect()) {
            led=!led;
            Thread::wait(500);
      }
      led=0;
      FILE * fp = fopen("/usb/extended_public_key.txt", "a");
      if (fp != NULL) {        
        std::string temp_str="";
        char char_n[1] = {'\n'}; 
        char char_sig[1] = {'!'};       
        uint8_t entropy[16];           
        uint8_t temp_first_half;
        uint8_t temp_second_half;
        take_ref();
        for(int i =0 ; i<16; i++) {
             temp_first_half = c2hex(bit4ToHex(read_half_of_byte()));
             temp_second_half = c2hex(bit4ToHex(read_half_of_byte()));
             temp_first_half = (temp_first_half << 4);           
             entropy[i]=  temp_first_half|temp_second_half ;         
       }        
        
        const char* mnemonic= mnemonic_from_data(entropy, 16); 
        HDPrivateKey hd_root(mnemonic,"");
        //HDPrivateKey hd_account=hd_root.derive("m/84'/1'/0'");
        //HDPrivateKey hd_account=hd_root.derive("m/49'/1'/0'");
        HDPrivateKey hd_account=hd_root.derive("m/84'/1'/0'");
        // extended public key
        std::string xpub_account=hd_account.xpub();
        char* char_xpub_account = &xpub_account[0];
        fprintf(fp, char_xpub_account);
        fprintf(fp, char_n);
        fclose(fp);
      
        FILE * fin = fopen("/usb/psbt.txt", "r");
        FILE * finc = fopen("/usb/psbt.txt", "r");                                
        FILE * fout = fopen("/usb/signed.txt", "a");
        FILE * ftemp = fopen("/usb/temp.txt", "a");
        if (fin != NULL && fout != NULL && ftemp != NULL && finc != NULL) { 
              int n=0;
              while (!feof(finc)) {     
                fgetc(finc);
                n++;
              }                   
             char buf[n-1];                   
             uint8_t res[2*(n-1)];         
             fgets(buf, sizeof(buf), fin);                               
             
                
             //PSBT psbt;
             ElectrumTx psbt;                                                                 
             
             fromBase64(buf, sizeof(buf), res, sizeof(res));        
             encoding_format f=RAW;  
            
            // psbt.parse(res, sizeof(res), f);  
             psbt.parse(res,f);  
             

           
           
            
            
            std::string temp_str="";   
            if(psbt.isValid()){
               temp_str="valid";        
            }else{
               temp_str="nevalid";  
            }
   
            char* char_temp_str=&temp_str[0];     
            fprintf(ftemp, char_temp_str);
            fprintf(ftemp, char_n);                           
            for(int i=0; i<psbt.tx.outputsNumber; i++){
                temp_str=psbt.tx.txOuts[i].address(&Testnet);
                char* char_temp_str1=&temp_str[0];
                fprintf(ftemp, char_temp_str1);
                fprintf(ftemp, char_n);            
            }            
    
            uint8_t signed_counter = psbt.sign(hd_account);  
            int signed_counter_int =(int)signed_counter;
            for(int i = 0;i < signed_counter_int;i++){                
                fprintf(ftemp, char_sig);
                fprintf(ftemp, char_n);
                }
            
            

            std::string signed_tx = psbt.toString();        
            char* char_signed_tx = &signed_tx[0];
            fprintf(fout, char_signed_tx);
                    
        }
        fclose(ftemp);
        fclose(fin);
        fclose(finc);
        fclose(fout);                            
       }   
        while(true) {
        led=!led;
        Thread::wait(500);
      } 
       
}  
