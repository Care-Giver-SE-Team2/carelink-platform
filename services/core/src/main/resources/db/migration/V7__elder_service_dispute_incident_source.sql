ALTER TABLE incident
    MODIFY COLUMN source
        ENUM(
            'CAREGIVER',
            'ELDER_SOS',
            'ELDER_SERVICE_DISPUTE',
            'SYSTEM_MISSED_CHECKIN'
        )
        NOT NULL;