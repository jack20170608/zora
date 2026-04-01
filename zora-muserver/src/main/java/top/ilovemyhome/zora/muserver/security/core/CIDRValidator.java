package top.ilovemyhome.zora.muserver.security.core;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;

import java.net.InetAddress;

public class CIDRValidator implements IpValidator {

    @Override
    public boolean isValid(String ip) {
        InetAddress remoteAddress = null;
        try {
            remoteAddress = InetAddress.getByName(ip);
        } catch (Exception e) {
            return false;
        }
        if (!remoteAddress.getClass().equals(requiredAddress.getClass())) {
            return false;
        }
        if (nMaskBits == -1) {
            return remoteAddress.equals(requiredAddress);
        }
        byte[] remoteAddressBytes = remoteAddress.getAddress();
        byte[] requiredAddressBytes = requiredAddress.getAddress();
        int fullBytes = nMaskBits / 8;
        int remainingBits = nMaskBits % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (remoteAddressBytes[i] != requiredAddressBytes[i]) {
                return false;
            }
        }
        if (remainingBits > 0 && fullBytes < remoteAddressBytes.length) {
            int mask = 0xFF << (8 - remainingBits);
            if ((remoteAddressBytes[fullBytes] & mask) != (requiredAddressBytes[fullBytes] & mask)) {
                return false;
            }
        }
        return true;
    }


    /**
     * Create a CIDR validator
     *
     * @param cidr the CIDR string, e.g. 192.168.0.0/24
     */
    public CIDRValidator(String cidr) {
        String ipAddress = cidr;
        if (Strings.CS.indexOf(cidr, "/") > 0) {
            ipAddress = StringUtils.substringBefore(cidr, "/");
            String nBits = StringUtils.substringAfter(cidr, "/");
            nMaskBits = Integer.parseInt(nBits);
        } else {
            nMaskBits = -1;
        }
        try {
            requiredAddress = InetAddress.getByName(ipAddress);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress, e);
        }
        assert (requiredAddress.getAddress().length == 4 || requiredAddress.getAddress().length == 16)
            : "IP address must be IPv4 or IPv6";
        assert (requiredAddress.getAddress().length * 8 >= nMaskBits)
            : "Mask bits more than address length";
    }

    private final int nMaskBits;
    private final InetAddress requiredAddress;
}
