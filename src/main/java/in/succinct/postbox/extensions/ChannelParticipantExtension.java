package in.succinct.postbox.extensions;

import com.venky.core.collections.SequenceSet;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.extensions.ParticipantExtension;
import com.venky.swf.db.model.User;
import in.succinct.postbox.db.model.Channel;

import java.util.List;

public class ChannelParticipantExtension extends ParticipantExtension<Channel> {
    static {
        registerExtension(new ChannelParticipantExtension());
    }
    
    @Override
    protected List<Long> getAllowedFieldValues(User user, Channel partiallyFilledModel, String fieldName) {
        List<Long> ret = null ;
        if (ObjectUtil.equals("CREATOR_USER_ID",fieldName)){
            ret = new SequenceSet<>();
            ret.add(user.getId());
        }else if (ObjectUtil.equals("ANY_USER_ID",fieldName)){
            ret = new SequenceSet<>();
            ret.add(user.getId());
        }
        
        return ret;
    }
}
