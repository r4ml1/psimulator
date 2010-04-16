/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package pocitac;

import datoveStruktury.*;
/**
 *
 * @author neiss
 */
public class LinuxPocitac extends AbstractPocitac {
    
    public LinuxPocitac(String jmeno, int port) {
        super(jmeno,port);
        ip_forward=false; // Linux defaultne nepreposila. Pak, myslim, zpatky nic neposle a paket proste zahodi.
    }

    /**
     * Ethernetove prijima nebo odmita me poslany pakety.
     * @param p
     * @param rozhr rozhrani pocitace, kterej ma paket prijmoutm, tzn. tohodle pocitace
     * @param ocekavana adresa, kterou na rozhrani ocekavam
     * @return true, kdyz byl paket prijmut, jinak false
     */
    @Override
    public boolean prijmiEthernetove(Paket p, SitoveRozhrani rozhr, IpAdresa ocekavana){
        if (rozhr.obsahujeStejnouAdresu(ocekavana)) { //adresa souhlasi - muzu to prijmout
            prijmiPaket(p);
            return true;
        } else {//adresa nesouhlasi, zpatky se musi poslat host unreachable
            return false;
        }
    }

    
}
