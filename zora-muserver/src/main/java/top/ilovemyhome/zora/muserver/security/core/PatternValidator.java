package top.ilovemyhome.zora.muserver.security.core;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

public class PatternValidator implements IpValidator {

    public PatternValidator(String patternStr) {
        this.allowPatternStr = patternStr;
    }

    @Override
    public boolean isValid(String ip) {
        if (StringUtils.isEmpty(allowPatternStr)){
            return false;
        }
        Pattern pattern = Pattern.compile(allowPatternStr);
        return pattern.matcher(ip).matches();
    }

    private final String allowPatternStr;

}
