-- The V2 seed creators were inserted before profile_complete/KYC/payout columns
-- existed (added in V9). Their profile_complete defaulted to false, which hides
-- them from every public catalog query (list/top/for-you all filter on
-- profile_complete = true), even though they have a portfolio item and are
-- verified. Backfill demo KYC/payout data so they satisfy the same
-- completeness rule as real onboarded creators (see
-- CreatorService.recomputeProfileComplete), then mark them complete.
UPDATE creator_profiles
SET id_document_number = 'AA1234567',
    id_document_url    = 'https://example.com/kyc/demo_passport.jpg',
    payout_card         = '8600123456789012',
    profile_complete    = true
WHERE user_id IN (
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003'
)
AND profile_complete = false;
