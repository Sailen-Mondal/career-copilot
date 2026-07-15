insert into job (id, source, external_id, url, company, title, skills_required, status, scraped_at)
values ('905a2fe6-512f-4069-924f-0684df5858c9', 'greenhouse', '8037358', 'https://boards.greenhouse.io/6sense/jobs/8037358?gh_jid=8037358', '6sense', 'Software Engineer III (Full Stack)', '["Java", "Spring Boot", "React", "TypeScript", "HTML", "CSS"]'::jsonb, 'active', now())
on conflict (id) do nothing;
