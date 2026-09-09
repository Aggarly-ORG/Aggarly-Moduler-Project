-- ============================================================================
-- V41: Host Operations, Reviews Responses, Watchdogs & Nocturnal Help System
-- ============================================================================

-- 1. Reviews: Add Host Curatorial Response and Astrophotography/Quietude Ratings
ALTER TABLE reviews
    ADD COLUMN IF NOT EXISTS host_response TEXT,
    ADD COLUMN IF NOT EXISTS host_responded_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS quietude_rating INT,
    ADD COLUMN IF NOT EXISTS optics_rating INT;

-- 2. Wishlists: Add Share Token for unguessable private shared-board links
ALTER TABLE wishlists
    ADD COLUMN IF NOT EXISTS share_token VARCHAR(64) UNIQUE;

-- 3. Cleaning / Turnover: Add Acoustic Decibel Readings & Silence Certification
ALTER TABLE cleaning_tasks
    ADD COLUMN IF NOT EXISTS acoustic_db_reading DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS silence_certified BOOLEAN DEFAULT FALSE;

-- 4. User Watchdogs: Autonomous Price Drop & Availability Radar
CREATE TABLE IF NOT EXISTS user_watchdogs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    property_id UUID REFERENCES properties(id) ON DELETE CASCADE,
    sanctuary_title VARCHAR(255),
    sanctuary_location VARCHAR(255),
    bortle_rating VARCHAR(50),
    target_dates VARCHAR(100),
    original_price NUMERIC(12,2),
    target_price NUMERIC(12,2),
    current_price NUMERIC(12,2),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    notifications_channel VARCHAR(100) DEFAULT 'SMS & Push',
    solstice_trigger BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_watchdogs_user ON user_watchdogs(user_id);
CREATE INDEX IF NOT EXISTS idx_watchdogs_property ON user_watchdogs(property_id);
CREATE INDEX IF NOT EXISTS idx_watchdogs_active ON user_watchdogs(is_active);

-- 5. Help Articles & Dark-Sky Knowledge Base
CREATE TABLE IF NOT EXISTS help_articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slug VARCHAR(100) UNIQUE NOT NULL,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_help_articles_category ON help_articles(category);

-- Pre-seed foundational dark-sky knowledge base articles
INSERT INTO help_articles (slug, title, category, content)
VALUES
    ('bortle-dark-sky-covenants', 'Bortle Dark-Sky Covenants & Optical Etiquette', 'covenants',
     'All Aggarly sanctuaries reside inside International Dark-Sky Association (IDA) certified or Class 1-3 Bortle zones. All guests are bound by covenant to extinguish exterior lighting past astronomical twilight (21:30 CEST) and utilize provided 620nm deep-red wavelength headlamps only.'),
    ('keyless-vault-access-protocol', 'Keyless Vault & Sanctuary Entry Protocol', 'access',
     'Vault door lockboxes activate at precisely 16:00 on arrival day. Enter your one-time residency PIN followed by the pound sign (#). If optical sensors fail due to extreme temperature, locate the secondary magnetic keycard in the auxiliary crypt box.'),
    ('telescope-collimation-calibration', 'Schmidt-Cassegrain Telescope Collimation Guide', 'equipment',
     'Each observatory suite contains a computer-guided Schmidt-Cassegrain telescope calibrated prior to arrival. To recalibrate following transit or seismic settling, align with Polaris using the hand controller star alignment routine.'),
    ('nocturnal-climate-and-acoustic-guidelines', 'Nocturnal Climate & Silence Certification', 'amenities',
     'All rooms feature acoustic baffling with background decibel targets below 24 dBA. Climate management systems are configured to cycle passively during deep observation hours to eliminate mechanical vibrations.')
ON CONFLICT (slug) DO NOTHING;

-- 6. Emergency Nocturnal Signals & Support Tickets
CREATE TABLE IF NOT EXISTS help_signals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    residency_ref VARCHAR(100),
    situation_type VARCHAR(100) NOT NULL,
    description TEXT NOT NULL,
    phone VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'DISPATCHED',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    resolved_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_help_signals_user ON help_signals(user_id);
CREATE INDEX IF NOT EXISTS idx_help_signals_status ON help_signals(status);