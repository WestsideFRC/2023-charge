package frc.robot.subsystems;

import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import com.revrobotics.RelativeEncoder;
import edu.wpi.first.wpilibj.AnalogEncoder;
import edu.wpi.first.wpilibj.AnalogInput;
import frc.robot.Constants.Swerve;
import com.revrobotics.CANSparkMax.IdleMode;

public class SwerveModule {
    public int moduleNumber;
    
    private CANSparkMax angleMotor;
    private CANSparkMax driveMotor;

    private RelativeEncoder integratedDriveEncoder;
    private RelativeEncoder integratedAngleEncoder;

    private AnalogInput input;
    private AnalogEncoder angleEncoder;

    private double initAngle, offsetAngle;
    public double calculatedAngle, reverseDriveMotor, errorAngle, lastErrorAngle;
    public double inputAngleVoltage, integral, derivative;
    public boolean correctAngle;
    public double driveSpeed;
    
    /**
     * Creates a SwerveModule object that represents a corresponding swerve module on the robot
     * <p>
     * This object contains methods that can be used to control and calculate wheel heading and speed
     * <p>
     * Created objects should be used together as a drivetrain where they can be controlled to move the robot
     * @param moduleNumber moduleID number
     * @param angleMotorID CAN Bus ID of the angle motor
     * @param driveMotorID CAN Bus ID of the drive motor
     * @param threncID ID of the thriftyBot analog encoder
     */
    public SwerveModule(int moduleNumber, int angleMotorID, int driveMotorID, int threncID) {
        this.moduleNumber = moduleNumber;
        // creates an object that will be used to control the motor controller for each motor
        angleMotor = new CANSparkMax(angleMotorID, MotorType.kBrushless);
        driveMotor = new CANSparkMax(driveMotorID, MotorType.kBrushless);

        // sets maximum current each motor can pull
        angleMotor.setSmartCurrentLimit(Swerve.angleMotorCurrentLimit);
        driveMotor.setSmartCurrentLimit(Swerve.driveMotorCurrentLimit);

        // sets drive motor behaviour when motor is neither commanded to move forward or backwards
        // brake: motor will attempt to stop movement
        // coast: motor will not attempt to stop, but it will not try to move either
        driveMotor.setIdleMode(IdleMode.kCoast);

        // gets the internal encoder on the drive and angle motor
        integratedDriveEncoder = driveMotor.getEncoder();
        integratedAngleEncoder = angleMotor.getEncoder();
        
        // gets the thritybot absolute encoder that is reading the turn shaft
        input = new AnalogInput(threncID);
        angleEncoder = new AnalogEncoder(input);
    }

    // below are methods that get critical electronics of each module
    
    /**
     * Returns motor controller object that is controlling the the angle motor
     * @return <B>SparkMax</B> that interfaces with the motor controller for angle motor
     */
    public CANSparkMax get_angleMotor() {
        return angleMotor;
    }

    /**
     * Returns motor controller object that is controlling the the drive motor
     * @return <B>SparkMax</B> that interfaces with the motor controller for drive motor
     */
    public CANSparkMax get_driveMotor() {
        return driveMotor;
    }

    /**
     * Returns integrated encoder object for the angle motor
     * @return <B>RelativeEncoder</B> that interfaces the integrated encoder inside the angle motor
     */
    public RelativeEncoder get_integratedDriveEncoder() {
        return integratedDriveEncoder;
    }

    /**
     * Returns integrated encoder object for the drive motor
     * @return <B>RelativeEncoder</B> that interfaces the integrated encoder inside the drive motor
     */
    public RelativeEncoder get_integratedAngleEncoder() {
        return integratedAngleEncoder;
    }

    /**
     * Returns analog encoder object for wheel direction
     * @return <B>AnalogEncoder</B> that interfaces with the analog encoder on the module
     */
    public AnalogEncoder get_angleEncoder() {
        return angleEncoder;
    }

    // below are methods that operate the drivetrain

    /**
     * Sets starting angle of the swerve module to define offsets for the angle encoder
     * @param initangle angle of drivetrain when initialized
     */
    public void setInitAngle(double initangle) {
        initAngle = initangle;
        // sets offset angle for the module
        offsetAngle = angleEncoder.getAbsolutePosition()*360 - initAngle;
    }

    /**
     * Finds the angle of the angleMotor when it is called
     * 
     * This method uses the absolute position of the angle encoder as the measurement device
     * <p>
     * 0 <= <B>calculatedAngle</B> < 360
     */
    public void findAngle() {
        calculatedAngle = angleEncoder.getAbsolutePosition()*360 - offsetAngle;

        // uses unit circle to find equivalent angle between 0 and 360
        if (calculatedAngle >= 360) {
          calculatedAngle -= 360;
        }
        else if (calculatedAngle < 0) {
          calculatedAngle += 360;
        }
    }

    /**
     * Finds and returns the error between a target value and the current value. 
     * This method also changes the direction of the drive motor to meet that value optimally
     * <p>
     * This method should be used as part of the drivetrain to control the heading of a module's wheel
     * @param target desired heading of a module's wheel
     * @param current current heading of a module's wheel
     * @return <B>error</B>(difference) between the current and desired heading
     */
    public double findError(double target, double current) {
        double error = 0.0;

        if (target >= current) { // when current value is greater than that of the setpoint 
            //normal
            error = target - current; 
            //optimal
            if (error >= 90) {
                reverseDriveMotor = -1.0;
                if (error >= 180) {
                    error = target - 180 - current;
                }
                else if (error >= 90) {
                    error = -1*(180 - target + current);
                }
            }
            else {
                reverseDriveMotor = 1.0;
            }
        }
        else { // when current value is less than that of the setpoint 
            //normal
            error = target + 360 - current;
            //optimal
            if (error >= 90) {
                reverseDriveMotor = -1.0;
                if (error >= 180) {
                    error = -1*(current - 180 - target);
                }
                else {
                    error = 180 + target - current;
                }
            }
            else {
                reverseDriveMotor = 1.0;
            }
        }
        return error;
    }
    
    public void pidController() {
        inputAngleVoltage = errorAngle * Swerve.angleKP; //p

        if (Math.abs(errorAngle) >= Swerve.errorTolerance) {
            integral += Math.abs(errorAngle); //i
        }
        derivative = errorAngle - lastErrorAngle; //d
        lastErrorAngle = errorAngle;
        
        inputAngleVoltage = -1.0 * (inputAngleVoltage + integral*Swerve.angleKI + derivative*Swerve.angleKD);

        if (Math.abs(inputAngleVoltage) > Swerve.maxVolt) {
            if (errorAngle < 0) {
                inputAngleVoltage = Swerve.maxVolt;
            }
            else {
                inputAngleVoltage = Swerve.maxVolt*-1;
            }
        }

        if (Math.abs(errorAngle) <= Swerve.errorTolerance) {
            inputAngleVoltage = 0;
            correctAngle = true;
        }
        else {
            correctAngle = false;
            if (Math.abs(inputAngleVoltage) < Swerve.minVolt) {
                if (errorAngle < 0) {
                    inputAngleVoltage = Swerve.minVolt;
                }
                else {
                    inputAngleVoltage = Swerve.minVolt*-1;
                }
            }
        }
        angleMotor.setVoltage(inputAngleVoltage);
    }

    
}