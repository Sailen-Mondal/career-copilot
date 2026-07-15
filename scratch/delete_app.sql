update application set resume_version_id = null, cover_letter_version_id = null where id = '55155926-5154-48dc-8011-1dd0ef9ceeb2';
delete from generated_document where application_id = '55155926-5154-48dc-8011-1dd0ef9ceeb2';
delete from application where id = '55155926-5154-48dc-8011-1dd0ef9ceeb2';
