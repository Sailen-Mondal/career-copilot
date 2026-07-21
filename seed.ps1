# PowerShell script to seed profile, facts, sync jobs, and create applications

$headers = @{
    "Content-Type" = "application/json"
    "X-API-Key" = "copilot-dev-key"
}

Write-Host "1. Creating candidate profile..." -ForegroundColor Cyan
$profileBody = @{
    name = "Jane Doe"
    email = "jane.doe@example.com"
    phone = "+15551234567"
    linkedinUrl = "https://linkedin.com/in/janedoe"
    websiteUrl = "https://janedoe.dev"
    workAuthorization = "US_CITIZEN"
    visaSponsorshipNeeded = $false
    salaryFloor = 120000
    locations = @("remote", "San Francisco")
    remotePreference = "REMOTE"
    blocklistCompanies = @("BlockedCo")
    dailyApplicationCap = 5
    autonomyThreshold = 80
    searchKeywords = @("software", "engineer", "developer", "backend", "fullstack", "java", "react")
} | ConvertTo-Json

$profileResponse = Invoke-RestMethod -Uri "http://localhost:8080/api/profile" -Method Post -Headers $headers -Body $profileBody
$profileId = $profileResponse.id
Write-Host "Profile created with ID: $profileId" -ForegroundColor Green

Write-Host "`n2. Adding profile facts (so groundedness passes)..." -ForegroundColor Cyan
$fact1 = @{
    type = "EXPERIENCE"
    employer = "Tech Corp"
    title = "Senior Software Engineer"
    startDate = "2020-01-01"
    endDate = "2023-01-01"
    bulletText = "Designed and built high-performance distributed systems using Java and Spring Boot. Integrated modular monoliths with Redis Streams."
    skills = @("Java", "Spring Boot", "Redis")
} | ConvertTo-Json

$fact2 = @{
    type = "EDUCATION"
    employer = "Stanford University"
    title = "B.S. Computer Science"
    startDate = "2015-09-01"
    endDate = "2019-06-01"
    bulletText = "Graduated with honors in Computer Science."
    skills = @("Algorithms", "Databases")
} | ConvertTo-Json

Invoke-RestMethod -Uri "http://localhost:8080/api/profile/facts" -Method Post -Headers $headers -Body $fact1 | Out-Null
Invoke-RestMethod -Uri "http://localhost:8080/api/profile/facts" -Method Post -Headers $headers -Body $fact2 | Out-Null
Write-Host "Profile facts added successfully." -ForegroundColor Green

Write-Host "`n3. Triggering Greenhouse job sync..." -ForegroundColor Cyan
$jobs = Invoke-RestMethod -Uri "http://localhost:8080/api/sources/greenhouse/sync?board=google" -Method Post -Headers $headers
Write-Host "Synced $($jobs.Count) new jobs from Greenhouse feed." -ForegroundColor Green

if ($jobs.Count -eq 0) {
    Write-Host "No new jobs synced. Fetching existing jobs from the database..." -ForegroundColor Yellow
    $jobs = Invoke-RestMethod -Uri "http://localhost:8080/api/jobs" -Method Get -Headers $headers
    Write-Host "Found $($jobs.Count) existing jobs in the database." -ForegroundColor Green
}

if ($jobs.Count -eq 0) {
    Write-Host "No jobs found in the database. Please check if backend is running." -ForegroundColor Red
    exit
}

Write-Host "`n4. Creating applications and executing the safety workflow..." -ForegroundColor Cyan
foreach ($job in $jobs) {
    $appBody = @{
        jobId = $job.id
        profileId = $profileId
    } | ConvertTo-Json
    
    try {
        $app = Invoke-RestMethod -Uri "http://localhost:8080/api/applications" -Method Post -Headers $headers -Body $appBody
        Write-Host "Created application for: $($job.title) at $($job.company) -> Match Score: $($app.matchScore) -> Status: $($app.status)" -ForegroundColor Yellow
    } catch {
        Write-Host "Failed to create application for $($job.title): $_" -ForegroundColor Red
    }
}

Write-Host "`nSeeding completed! Check your browser dashboard and worker logs now!" -ForegroundColor Green
