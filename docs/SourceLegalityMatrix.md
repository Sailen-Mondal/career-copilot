# Source Legality Matrix

This file should be reviewed before adding any source. Favor public APIs and manual import over authenticated scraping.

| Source | Access Method | Login Required | Automation Risk | MVP Decision |
|---|---:|---:|---:|---|
| Greenhouse public boards | Public JSON API | No | Low | Use first |
| Lever public postings | Public API/page data | No | Low | Use second |
| Company career pages | Public pages/API varies | No | Medium | Add selectively |
| LinkedIn | Authenticated web app | Yes | High | Manual import only |
| Indeed | Authenticated or restricted web app | Often | High | Manual import only |
| Workday | Browser forms | Often | Medium-High | Shadow mode only first |

## Required Fields Per Source

- Terms or API reference checked
- Rate limit configured
- Robots.txt behavior documented where relevant
- Dedup key strategy
- Expiration/liveness check
- Failure and retry policy
