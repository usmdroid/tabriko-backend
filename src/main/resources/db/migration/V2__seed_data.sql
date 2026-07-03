-- Seed data for local development

-- Categories
INSERT INTO categories (name, icon_url) VALUES
    ('Xonanda',  'https://example.com/icons/xonanda.png'),
    ('Bloger',   'https://example.com/icons/bloger.png'),
    ('Viner',    'https://example.com/icons/viner.png'),
    ('Aktyor',   'https://example.com/icons/aktyor.png'),
    ('Sportchi', 'https://example.com/icons/sportchi.png')
ON CONFLICT (name) DO NOTHING;

-- Superadmin user (phone: +998901234567, OTP: 123456 in dev)
INSERT INTO users (id, phone, name, email, role, status) VALUES
    ('00000000-0000-0000-0000-000000000001', '+998901234567', 'Admin User', 'admin@tabriko.uz', 'SUPERADMIN', 'ACTIVE')
ON CONFLICT (phone) DO NOTHING;

-- Moderator
INSERT INTO users (id, phone, name, email, role, status) VALUES
    ('00000000-0000-0000-0000-000000000002', '+998901234568', 'Moderator', 'mod@tabriko.uz', 'MODERATOR', 'ACTIVE')
ON CONFLICT (phone) DO NOTHING;

-- Demo creators
INSERT INTO users (id, phone, name, role, status) VALUES
    ('10000000-0000-0000-0000-000000000001', '+998901111001', 'Jahongir Xoliqov', 'CREATOR', 'ACTIVE'),
    ('10000000-0000-0000-0000-000000000002', '+998901111002', 'Malika Rahimova',  'CREATOR', 'ACTIVE'),
    ('10000000-0000-0000-0000-000000000003', '+998901111003', 'Bekzod Komilов',   'CREATOR', 'ACTIVE')
ON CONFLICT (phone) DO NOTHING;

-- Creator profiles
INSERT INTO creator_profiles (user_id, category_id, bio, avg_rating, rating_count, price_from, delivery_days, is_top, is_verified) VALUES
    ('10000000-0000-0000-0000-000000000001',
     (SELECT id FROM categories WHERE name='Xonanda'),
     'O''zbekistonning mashhur xonandasi. Siz uchun maxsus tabrik videosin tayyorlayman!',
     4.80, 42, 150000, 2, true, true),
    ('10000000-0000-0000-0000-000000000002',
     (SELECT id FROM categories WHERE name='Bloger'),
     'YouTube va Instagram bloger. Kulgili va ijodiy tabriklar.',
     4.60, 28, 100000, 3, true, true),
    ('10000000-0000-0000-0000-000000000003',
     (SELECT id FROM categories WHERE name='Aktyor'),
     'Teatr va kino aktori. Chiroyli va ta''sirli tabriklar.',
     4.90, 15, 200000, 1, false, true)
ON CONFLICT (user_id) DO NOTHING;

-- Demo portfolio items
INSERT INTO portfolio_items (creator_id, media_url, is_public) VALUES
    ('10000000-0000-0000-0000-000000000001', 'https://example.com/portfolio/creator1_1.mp4', true),
    ('10000000-0000-0000-0000-000000000001', 'https://example.com/portfolio/creator1_2.mp4', true),
    ('10000000-0000-0000-0000-000000000002', 'https://example.com/portfolio/creator2_1.mp4', true),
    ('10000000-0000-0000-0000-000000000003', 'https://example.com/portfolio/creator3_1.mp4', true)
ON CONFLICT DO NOTHING;

-- Demo client user (phone: +998909999999, OTP: 123456)
INSERT INTO users (id, phone, name, role, status) VALUES
    ('20000000-0000-0000-0000-000000000001', '+998909999999', 'Demo Client', 'CLIENT', 'ACTIVE')
ON CONFLICT (phone) DO NOTHING;

-- Demo completed order + review
INSERT INTO orders (id, client_id, creator_id, type, option, recipient_name, recipient_occasion, price, status, deadline, created_at) VALUES
    ('30000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001',
     'VIDEO', 'HAZIL', 'Bobur', 'Tug''ilgan kun',
     150000, 'ACCEPTED',
     NOW() + INTERVAL '3 days', NOW() - INTERVAL '5 days')
ON CONFLICT DO NOTHING;

INSERT INTO deliveries (order_id, media_url_watermarked, media_url_clean, watermarked, delivered_at) VALUES
    ('30000000-0000-0000-0000-000000000001',
     'https://example.com/deliveries/order1_wm.mp4',
     'https://example.com/deliveries/order1_clean.mp4',
     false, NOW() - INTERVAL '4 days')
ON CONFLICT DO NOTHING;

INSERT INTO reviews (order_id, client_id, creator_id, stars, comment) VALUES
    ('30000000-0000-0000-0000-000000000001',
     '20000000-0000-0000-0000-000000000001',
     '10000000-0000-0000-0000-000000000001',
     5, 'Juda zo''r tabrik! Hammaga tavsiya qilaman.')
ON CONFLICT DO NOTHING;
