const cp = require('child_process');
cp.exec('powershell -Command "Get-CimInstance Win32_Process | Where-Object { $_.Name -match \'docker\' } | Select-Object ProcessId, Name, CommandLine | ConvertTo-Json"', (err, stdout, stderr) => {
    if (err) {
        console.error(err);
        return;
    }
    console.log(stdout);
});
