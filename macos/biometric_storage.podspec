#
# To learn more about a Podspec see http://guides.cocoapods.org/syntax/podspec.html.
# Run `pod lib lint biometric_storage.podspec' to validate before publishing.
#
Pod::Spec.new do |s|
  s.name             = 'biometric_storage'
  s.version          = '5.1.1-dev.2'
  s.summary          = 'Secure storage with optional biometric protection for Flutter.'
  s.description      = <<-DESC
Secure storage with optional biometric protection for Flutter apps.
                       DESC
  s.homepage         = 'https://github.com/omar-hanafy/biometric_storage'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'omar-hanafy' => 'omar_hanafy@icloud.com' }
  s.source           = { :path => '.' }
  s.source_files     = 'Classes/**/*'
  s.dependency 'FlutterMacOS'

  s.platform = :osx, '10.15'
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES' }
  s.swift_version = '5.0'
end
