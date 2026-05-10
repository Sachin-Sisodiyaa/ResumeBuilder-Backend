$body = @{
    email = "test@gmail.com"
    password = "wrongpassword"
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/api/v1/auth/login" -Method Post -Body $body -ContentType "application/json"
    $response.Content
} catch {
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $errorContent = $reader.ReadToEnd()
    Write-Output "ERROR STATUS: $($_.Exception.Response.StatusCode)"
    Write-Output "ERROR BODY: $errorContent"
}
