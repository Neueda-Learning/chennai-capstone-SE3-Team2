package com.yellow.trade.kyc;

import com.yellow.trade.mappers.KycMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: outside this package, only {@link KycService} is visible. */
@Service
class KycVerificationService implements KycService {

    private final KycMapper mapper;

    KycVerificationService(KycMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void submit(long clientId) {
        mapper.insertPending(clientId);
    }
}
