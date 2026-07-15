update application set resume_version_id = null, cover_letter_version_id = null where id = 'c85a8406-9911-46e1-8ab3-76ae26b92198';
delete from generated_document where application_id = 'c85a8406-9911-46e1-8ab3-76ae26b92198';
delete from application where id = 'c85a8406-9911-46e1-8ab3-76ae26b92198';
